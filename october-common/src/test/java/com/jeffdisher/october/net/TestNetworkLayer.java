package com.jeffdisher.october.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;


public class TestNetworkLayer
{
	@Test
	public void serverTest() throws Throwable
	{
		// Start the server.
		int port = 3000;
		CountDownLatch connectLatch = new CountDownLatch(1);
		CountDownLatch disconnectLatch = new CountDownLatch(1);
		Packet[] holder = new Packet[1];
		CountDownLatch receiveLatch = new CountDownLatch(1);
		
		NetworkLayer<Integer> server = NetworkLayer.startListening(new NetworkLayer.IListener<Integer>()
		{
			@Override
			public Integer buildToken()
			{
				return 1;
			}
			@Override
			public void peerConnected(Integer token)
			{
				connectLatch.countDown();
			}
			@Override
			public void peerDisconnected(Integer token)
			{
				disconnectLatch.countDown();
			}
			@Override
			public void peerReadyForWrite(Integer token)
			{
				// We ignore this in this test.
			}
			@Override
			public void packetReceived(Integer token, Packet packet)
			{
				Assert.assertNull(holder[0]);
				holder[0] = packet;
				receiveLatch.countDown();
			}
		}, port);
		
		// Connect a client and observe that we see a connected peer.
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		connectLatch.await();
		
		// Now, both sides should be able to send a message, right away (we will just use the ID assignment, since it is simple).
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		PacketCodec.serializeToBuffer(buffer, new Packet_ServerSendConfiguration(1, 100L));
		buffer.flip();
		client.write(buffer);
		
		server.sendMessage(1, new Packet_ServerSendConfiguration(2, 100L));
		
		// Verify that we received both.
		receiveLatch.await();
		Assert.assertEquals(1, ((Packet_ServerSendConfiguration) holder[0]).clientId);
		
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet clientRead = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertEquals(2, ((Packet_ServerSendConfiguration) clientRead).clientId);
		
		// Close the client and verify that the server sees the disconnect.
		client.close();
		disconnectLatch.await();
		server.stop();
	}

	@Test
	public void clientTest() throws Throwable
	{
		int port = 3000;
		// We want to fake up a server.
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel socket = ServerSocketChannel.open();
		socket.bind(address);
		
		Packet[] holder = new Packet[1];
		CountDownLatch receiveLatch = new CountDownLatch(1);
		
		NetworkLayer<Void> client = NetworkLayer.connectToServer(new NetworkLayer.IListener<Void>()
		{
			@Override
			public Void buildToken()
			{
				return null;
			}
			@Override
			public void peerConnected(Void token)
			{
				throw new AssertionError("Not in client mode");
			}
			@Override
			public void peerDisconnected(Void token)
			{
				throw new AssertionError("Not in client mode");
			}
			@Override
			public void peerReadyForWrite(Void token)
			{
				// We ignore this in this test.
			}
			@Override
			public void packetReceived(Void token, Packet packet)
			{
				Assert.assertNull(holder[0]);
				holder[0] = packet;
				receiveLatch.countDown();
			}
		}, InetAddress.getLocalHost(), port);
		SocketChannel server = socket.accept();
		
		// Now, both sides should be able to send a message, right away (we will just use the ID assignment, since it is simple).
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		PacketCodec.serializeToBuffer(buffer, new Packet_ServerSendConfiguration(1, 100L));
		buffer.flip();
		server.write(buffer);
		
		client.sendMessage(null, new Packet_ServerSendConfiguration(2, 100L));
		
		// Verify that we received both.
		receiveLatch.await();
		Assert.assertEquals(1, ((Packet_ServerSendConfiguration) holder[0]).clientId);
		
		buffer.clear();
		server.read(buffer);
		buffer.flip();
		Packet clientRead = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertEquals(2, ((Packet_ServerSendConfiguration) clientRead).clientId);
		
		// Shut down everything.
		client.stop();
		server.close();
		socket.close();
	}
}
