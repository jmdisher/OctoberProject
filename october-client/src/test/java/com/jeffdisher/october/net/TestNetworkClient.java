package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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
		};
		
		NetworkClient client1 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port);
		SocketChannel connection1 = _serverHandshake(server, 1);
		NetworkClient client2 = new NetworkClient(emptyListener, InetAddress.getLocalHost(), port);
		SocketChannel connection2 = _serverHandshake(server, 2);
		
		client1.stop();
		client2.stop();
		
		connection1.close();
		connection2.close();
		server.close();
	}

	@Test
	public void largestPacket() throws Throwable
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
		CuboidData cuboid = CuboidData.createNew(cuboidAddress, new IOctree[] { octree, OctreeObject.create()});
		
		// We should be able to send this as 1 start packet and 2 fragment packets.
		CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(cuboid);
		Packet_CuboidStart start = (Packet_CuboidStart) serializer.getNextPacket();
		Packet_CuboidFragment frag1 = (Packet_CuboidFragment) serializer.getNextPacket();
		Packet_CuboidFragment frag2 = (Packet_CuboidFragment) serializer.getNextPacket();
		Assert.assertNull(serializer.getNextPacket());
		Packet[] outgoing = new Packet[] { start, frag1, frag2 };
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		// Connect the client.
		Packet[] received = new Packet[3];
		CountDownLatch latch = new CountDownLatch(1);
		NetworkClient[] holder = new NetworkClient[1];
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			int _nextIndex = 0;
			@Override
			public void networkReady()
			{
				// We don't send anything from the client in this case.
			}
			@Override
			public void packetReceived(Packet packet)
			{
				received[_nextIndex] = packet;
				_nextIndex += 1;
				if (received.length == _nextIndex)
				{
					latch.countDown();
				}
			}
		}, InetAddress.getLocalHost(), port);
		holder[0] = client;
		SocketChannel connection = _serverHandshake(server, 1);
		
		// Send these from the server and wait to see them on the client side.
		_sendServerMessage(connection, outgoing[0]);
		_sendServerMessage(connection, outgoing[1]);
		_sendServerMessage(connection, outgoing[2]);
		latch.await();
		
		client.stop();
		connection.close();
		server.close();
		
		// Now, deserialize these.
		CuboidCodec.Deserializer deserializer = new CuboidCodec.Deserializer((Packet_CuboidStart) received[0]);
		Assert.assertNull(deserializer.processPacket((Packet_CuboidFragment) received[1]));
		CuboidData finished = deserializer.processPacket((Packet_CuboidFragment) received[2]);
		Assert.assertNotNull(finished);
		
		// Verify that a few of the entries are consistent.
		Assert.assertEquals(0, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals((32 * 32 * 10) + (32 * 10) + 10, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)10, (byte)10, (byte)10)));
		Assert.assertEquals((32 * 32 * 30) + (32 * 30) + 30, finished.getData15(AspectRegistry.BLOCK, new BlockAddress((byte)30, (byte)30, (byte)30)));
	}


	private SocketChannel _serverHandshake(ServerSocketChannel server, int clientId) throws IOException, UnknownHostException
	{
		SocketChannel connection = server.accept();
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		PacketCodec.serializeToBuffer(buffer, new Packet_AssignClientId(clientId));
		buffer.flip();
		connection.write(buffer);
		return connection;
	}

	private void _sendServerMessage(SocketChannel connection, Packet packet) throws IOException, UnknownHostException
	{
		ByteBuffer buffer = ByteBuffer.allocate(NetworkLayer.BUFFER_SIZE_BYTES);
		PacketCodec.serializeToBuffer(buffer, packet);
		buffer.flip();
		connection.write(buffer);
	}
}
