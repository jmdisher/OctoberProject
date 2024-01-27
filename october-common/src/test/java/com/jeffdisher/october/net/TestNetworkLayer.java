package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


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
		PacketCodec.serializeToBuffer(buffer, new Packet_AssignClientId(1));
		buffer.flip();
		client.write(buffer);
		
		server.sendMessage(1, new Packet_AssignClientId(2));
		
		// Verify that we received both.
		receiveLatch.await();
		Assert.assertEquals(1, ((Packet_AssignClientId) holder[0]).clientId);
		
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet clientRead = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertEquals(2, ((Packet_AssignClientId) clientRead).clientId);
		
		// Close the client and verify that the server sees the disconnect.
		client.close();
		disconnectLatch.await();
		server.stop();
	}

	@Test
	public void largestPacket() throws IOException
	{
		// We use a Packet_CuboidSetAspectShort with a worst-case aspect.
		OctreeShort octree = OctreeShort.create((short)0);
		System.out.println("Building worst-case octree");
		short value = 0;
		for (int x = 0; x < 32; ++x)
		{
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					octree.setData(new BlockAddress((byte)x, (byte)y, (byte)z), value);
					value += 1;
				}
			}
		}
		byte[] rawData = octree.copyRawData();
		// Get the fragments.
		Packet_CuboidSetAspectShortPart[] fragments = Packet_CuboidSetAspectShortPart.fragmentData(new CuboidAddress((short)0, (short)0, (short)0), 0, rawData);
		// We expect this to be split.
		Assert.assertEquals(2, fragments.length);
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		@SuppressWarnings("unchecked")
		NetworkLayer<Integer>[] holder = new NetworkLayer[1];
		NetworkLayer<Integer> server = NetworkLayer.startListening(new NetworkLayer.IListener<Integer>()
		{
			boolean _sent = false;
			@Override
			public Integer buildToken()
			{
				return 1;
			}
			@Override
			public void peerConnected(Integer token)
			{
				// Starts ready - we can send the message now.
				holder[0].sendMessage(token, fragments[0]);
			}
			@Override
			public void peerDisconnected(Integer token)
			{
			}
			@Override
			public void peerReadyForWrite(Integer token)
			{
				if (!_sent)
				{
					holder[0].sendMessage(token, fragments[1]);
					_sent = true;
				}
			}
			@Override
			public void packetReceived(Integer token, Packet packet)
			{
				// We don't expect this in these tests.
				Assert.fail();
			}
		}, port);
		holder[0] = server;
		
		// Connect the client.
		SocketChannel client1 = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		
		Packet_CuboidSetAspectShortPart read1 = (Packet_CuboidSetAspectShortPart)_readOnePacket(client1);
		Packet_CuboidSetAspectShortPart read2 = (Packet_CuboidSetAspectShortPart)_readOnePacket(client1);
		byte[] stitched = Packet_CuboidSetAspectShortPart.stitchPlanes(new Packet_CuboidSetAspectShortPart[] {
				read1,
				read2,
		});
		Assert.assertArrayEquals(rawData, stitched);
		
		client1.close();
		
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
		PacketCodec.serializeToBuffer(buffer, new Packet_AssignClientId(1));
		buffer.flip();
		server.write(buffer);
		
		client.sendMessage(null, new Packet_AssignClientId(2));
		
		// Verify that we received both.
		receiveLatch.await();
		Assert.assertEquals(1, ((Packet_AssignClientId) holder[0]).clientId);
		
		buffer.clear();
		server.read(buffer);
		buffer.flip();
		Packet clientRead = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Assert.assertEquals(2, ((Packet_AssignClientId) clientRead).clientId);
		
		// Shut down everything.
		client.stop();
		server.close();
		socket.close();
	}


	private Packet _readOnePacket(SocketChannel client1) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES);
		buffer.limit(PacketCodec.HEADER_BYTES);
		client1.read(buffer);
		Assert.assertTrue(buffer.position() >= PacketCodec.HEADER_BYTES);
		int requiredSize = Short.toUnsignedInt(buffer.getShort(0));
		if (0 == requiredSize)
		{
			requiredSize = 0x10000;
		}
		buffer.limit(requiredSize);
		while (buffer.position() < requiredSize)
		{
			client1.read(buffer);
		}
		buffer.flip();
		return PacketCodec.parseAndSeekFlippedBuffer(buffer);
	}
}
