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
			public void handshakeCompleted(int assignedId, long millisPerTick, int viewDistanceMaximum)
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
				// This should only be the initial config update packet after handshake.
				Assert.assertTrue(packet instanceof Packet_ServerSendConfigUpdate);
			}
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test", 1);
		SocketChannel connection1 = _serverHandshake(server, 1);
		NetworkClient client2 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test", 1);
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
			public void handshakeCompleted(int assignedId, long millisPerTick, int viewDistanceMaximum)
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
				// This should only be the initial config update packet after handshake.
				Assert.assertTrue(packet instanceof Packet_ServerSendConfigUpdate);
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
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test", 1);
		SocketChannel connection1 = _serverHandshake(server, 1);
		NetworkClient client2 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test", 1);
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
			public void handshakeCompleted(int assignedId, long millisPerTick, int viewDistanceMaximum)
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
				// This should only be the initial config update packet after handshake.
				Assert.assertTrue(packet instanceof Packet_ServerSendConfigUpdate);
			}
			@Override
			public void serverDisconnected()
			{
				// We aren't acting on this in our test.
			}
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port, "test", 1);
		SocketChannel connection1 = _serverHandshake(server, 1);
		latch.await();
		
		// Now, flood the network, lock-step with ready notifications.
		// Note that we only use Packet_SendChatMessage here since it is "some message type" for the test but the server internals will use it differently.
		for (int i = 0; i < 10000; ++i)
		{
			sync.waitFor();
			client1.sendMessage(new Packet_SendChatMessage(1, ""));
		}
		
		connection1.close();
		server.close();
		
		client1.stop();
	}

	@Test
	public void corruptPacket() throws IOException
	{
		// Show what happens when we receive a corrupt packet from the client.
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		NetworkClient client1 = new NetworkClient(new _ClientListener(), InetAddress.getLocalHost(), port, "test", 1);
		SocketChannel connection1 = _serverHandshake(server, 1);
		
		// Fill the socket with corrupt data (something which looks like a small but invalid packet):  size 0x10, ordinal 0.
		// (if this is too large, we will just wait for the rest of it).
		ByteBuffer buffer = ByteBuffer.allocate(64);
		buffer.putShort((short)0x10);
		buffer.put((byte)0);
		buffer.flip();
		// (we want to write the full buffer)
		buffer.limit(buffer.capacity());
		connection1.write(buffer);
		
		// Try to read since we should see the disconnect.
		buffer.clear();
		int sizeRead = connection1.read(buffer);
		Assert.assertEquals(-1, sizeRead);
		
		// Shut down.
		server.close();
		client1.stop();
	}

	@Test
	public void deniedPacket() throws IOException
	{
		// Show what happens when we receive a "from server" packet from the client.
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		NetworkClient client1 = new NetworkClient(new _ClientListener(), InetAddress.getLocalHost(), port, "test", 1);
		SocketChannel connection1 = _serverHandshake(server, 1);
		
		// We will send the "send chat" packet, since that is only intended to be TO a client, not from one.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		PacketCodec.serializeToBuffer(buffer, new Packet_SendChatMessage(0, "message"));
		buffer.flip();
		connection1.write(buffer);
		
		// Try to read since we should see the disconnect.
		buffer.clear();
		int sizeRead = connection1.read(buffer);
		Assert.assertEquals(-1, sizeRead);
		
		// Shut down.
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
		long millisPerTick = 100L;
		int viewDistanceMaximum = 3;
		PacketCodec.serializeToBuffer(buffer, new Packet_ServerSendClientId(clientId, millisPerTick, viewDistanceMaximum));
		// We immediately send a config update after the ID so sythesize that, too.
		int ticksPerDay = 1000;
		int dayStartTick = 0;
		PacketCodec.serializeToBuffer(buffer, new Packet_ServerSendConfigUpdate(ticksPerDay, dayStartTick));
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

	private static class _ClientListener implements NetworkClient.IListener
	{
		@Override
		public void handshakeCompleted(int assignedId, long millisPerTick, int viewDistanceMaximum)
		{
		}
		@Override
		public void networkReady()
		{
		}
		@Override
		public void packetReceived(Packet packet)
		{
		}
		@Override
		public void serverDisconnected()
		{
		}
	}
}
