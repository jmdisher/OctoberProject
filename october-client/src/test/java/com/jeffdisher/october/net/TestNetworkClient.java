package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;


public class TestNetworkClient
{
	@Test
	public void basicHandshakeDisconnect() throws IOException
	{
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		NetworkClient.IListener emptyListener = new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void networkReady()
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void packetReceived(Packet packet)
			{
				// Should not happen in this test.
				Assert.fail();
			}
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test");
		SocketChannel connection1 = _serverHandshake(server, 1);
		NetworkClient client2 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test");
		SocketChannel connection2 = _serverHandshake(server, 2);
		
		client1.stop();
		client2.stop();
		
		connection1.close();
		connection2.close();
		server.close();
	}

	@Test
	public void serverSideDisconnect() throws Throwable
	{
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		// We will use this listener for both so we want to see 2 disconnects happen and the final wait.
		CyclicBarrier barrier = new CyclicBarrier(3);
		NetworkClient.IListener emptyListener = new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void networkReady()
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void packetReceived(Packet packet)
			{
				// Should not happen in this test.
				Assert.fail();
			}
			@Override
			public void serverDisconnected()
			{
				try
				{
					barrier.await();
				}
				catch (InterruptedException | BrokenBarrierException e)
				{
					throw new AssertionError("Not in test", e);
				}
			}
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test");
		SocketChannel connection1 = _serverHandshake(server, 1);
		NetworkClient client2 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test");
		SocketChannel connection2 = _serverHandshake(server, 2);
		
		connection1.close();
		connection2.close();
		server.close();
		
		barrier.await();
		client1.stop();
		client2.stop();
	}

	@Test
	public void floodMessages() throws Throwable
	{
		// In this test, we will flood the client-side layer with messages, consuming them as quickly as possible on the server side, to verify that the client state doesn't break.
		// NOTE:  This test is just to find intermittent errors, so passing here doesn't prove that things are perfect.
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		// We will wait for the handshake before flooding.
		CountDownLatch latch = new CountDownLatch(1);
		// Use a simple sync object.
		_Sync sync = new _Sync();
		NetworkClient.IListener emptyListener = new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
				latch.countDown();
			}
			@Override
			public void networkReady()
			{
				sync.release();
			}
			@Override
			public void packetReceived(Packet packet)
			{
				// Should not happen in this test.
				Assert.fail();
			}
			@Override
			public void serverDisconnected()
			{
				// We aren't acting on this in our test.
			}
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test");
		SocketChannel connection1 = _serverHandshake(server, 1);
		latch.await();
		
		// Now, flood the network, lock-step with ready notifications.
		for (int i = 0; i < 10000; ++i)
		{
			sync.waitFor();
			client1.sendMessage(new Packet_Chat(1, ""));
		}
		
		connection1.close();
		server.close();
		
		client1.stop();
	}


	private SocketChannel _serverHandshake(ServerSocketChannel server, int clientId) throws IOException, UnknownHostException
	{
		SocketChannel connection = server.accept();
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Wait for the first step of the handshake.
		connection.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		Packet_ClientSendDescription fromClient = (Packet_ClientSendDescription) packet;
		Assert.assertNotNull(fromClient);
		
		// Send out response.
		buffer.clear();
		PacketCodec.serializeToBuffer(buffer, new Packet_ServerSendConfiguration(clientId, 100L));
		buffer.flip();
		connection.write(buffer);
		return connection;
	}


	private static class _Sync
	{
		private boolean _shouldRelease;
		public synchronized void release()
		{
			Assert.assertFalse(_shouldRelease);
			_shouldRelease = true;
			this.notify();
		}
		public synchronized void waitFor() throws InterruptedException
		{
			while (!_shouldRelease)
			{
				this.wait();
			}
			_shouldRelease = false;
		}
	}
}
