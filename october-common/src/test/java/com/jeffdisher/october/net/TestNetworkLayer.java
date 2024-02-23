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

import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.registries.AspectRegistry;
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
		// Combine this into a basic cuboid.
		CuboidAddress cuboidAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidData.createNew(cuboidAddress, new IOctree[] { octree, OctreeObject.create(), OctreeShort.create((short)0)});
		
		// We should be able to send this as 1 start packet and 2 fragment packets.
		CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(cuboid);
		Packet_CuboidStart start = (Packet_CuboidStart) serializer.getNextPacket();
		Packet_CuboidFragment frag1 = (Packet_CuboidFragment) serializer.getNextPacket();
		Packet_CuboidFragment frag2 = (Packet_CuboidFragment) serializer.getNextPacket();
		Assert.assertNull(serializer.getNextPacket());
		Packet[] outgoing = new Packet[] { start, frag1, frag2 };
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		@SuppressWarnings("unchecked")
		NetworkLayer<Integer>[] holder = new NetworkLayer[1];
		NetworkLayer<Integer> server = NetworkLayer.startListening(new NetworkLayer.IListener<Integer>()
		{
			int _nextIndex = 0;
			@Override
			public Integer buildToken()
			{
				return 1;
			}
			@Override
			public void peerConnected(Integer token)
			{
				// Starts ready - we can send the message now.
				holder[0].sendMessage(token, outgoing[_nextIndex]);
				_nextIndex += 1;
			}
			@Override
			public void peerDisconnected(Integer token)
			{
			}
			@Override
			public void peerReadyForWrite(Integer token)
			{
				if (_nextIndex < outgoing.length)
				{
					holder[0].sendMessage(token, outgoing[_nextIndex]);
					_nextIndex += 1;
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
		
		Packet_CuboidStart in1 = (Packet_CuboidStart)_readOnePacket(client1);
		Packet_CuboidFragment in2 = (Packet_CuboidFragment)_readOnePacket(client1);
		Packet_CuboidFragment in3 = (Packet_CuboidFragment)_readOnePacket(client1);
		
		client1.close();
		server.stop();
		
		// Now, deserialize these.
		CuboidCodec.Deserializer deserializer = new CuboidCodec.Deserializer(in1);
		Assert.assertNull(deserializer.processPacket(in2));
		CuboidData finished = deserializer.processPacket(in3);
		Assert.assertNotNull(finished);
		
		// Verify that a few of the entries are consistent.
		Assert.assertEquals(0, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals((32 * 32 * 10) + (32 * 10) + 10, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)10, (byte)10, (byte)10)));
		Assert.assertEquals((32 * 32 * 30) + (32 * 30) + 30, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)30, (byte)30, (byte)30)));
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
