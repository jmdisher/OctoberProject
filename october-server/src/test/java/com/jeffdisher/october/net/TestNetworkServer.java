package com.jeffdisher.october.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


public class TestNetworkServer
{
	@Test
	public void basicHandshakeDisconnect() throws IOException
	{
		int[] leftCount = new int[1];
		Map<Integer, String> joinNames = new HashMap<>();
		int port = 3000;
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			@Override
			public void userLeft(int id)
			{
				// We don't always see that the user has left if we shut down first.
				leftCount[0] += 1;
			}
			@Override
			public void userJoined(int id, String name)
			{
				Assert.assertFalse(joinNames.containsKey(id));
				joinNames.put(id, name);
			}
			@Override
			public void packetReceived(int id, Packet packet)
			{
				// Should not happen in this test.
				Assert.fail();
			}
			@Override
			public void networkReady(int id)
			{
				// We aren't acting on this in our test.
			}
		}, port);
		
		int client1 = _runClient(port, "Client 1");
		int client2 = _runClient(port, "Client 2");
		Assert.assertEquals(1, client1);
		Assert.assertEquals(2, client2);
		// We should see at least one disconnect, since we do these in series.
		Assert.assertTrue(leftCount[0] > 0);
		// Similarly, we will see the first client appear, since the disconnect-accept is lock-step on the server, but maybe not the second.
		Assert.assertEquals(joinNames.get(client1), "Client 1");
		
		server.stop();
	}

	@Test
	public void chat() throws IOException
	{
		int port = 3000;
		NetworkServer[] holder = new NetworkServer[1];
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			private List<String> _messagesFor1 = new ArrayList<>();
			private boolean _isReady1 = false;
			@Override
			public void userLeft(int id)
			{
			}
			@Override
			public void userJoined(int id, String name)
			{
				// Starts ready.
				_isReady1 = true;
			}
			@Override
			public void packetReceived(int id, Packet packet)
			{
				// We only expect chat messages.
				Packet_Chat chat = (Packet_Chat) packet;
				Assert.assertEquals(2, id);
				_messagesFor1.add(chat.message);
				_handle();
			}
			@Override
			public void networkReady(int id)
			{
				_isReady1 = true;
				_handle();
			}
			private void _handle()
			{
				if (_isReady1 && !_messagesFor1.isEmpty())
				{
					String message = _messagesFor1.remove(0);
					_isReady1 = false;
					holder[0].sendMessage(1, new Packet_Chat(2, message));
				}
			}
		}, port);
		holder[0] = server;
		
		// Connect both client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		SocketChannel client2 = _connectAndHandshakeClient(port, "Client 2");
		
		// Send messages from client 2 to 1.
		for (int i = 0; i < 10; ++i)
		{
			String message = "Chat " + i;
			ByteBuffer buffer = ByteBuffer.allocate(16);
			PacketCodec.serializeToBuffer(buffer, new Packet_Chat(0, message));
			buffer.flip();
			int written = client2.write(buffer);
			Assert.assertEquals(written, buffer.limit());
		}
		client2.close();
		
		// Read the messages from client 1.
		int next = 0;
		ByteBuffer buffer = ByteBuffer.allocate(16);
		while (next < 10)
		{
			client1.read(buffer);
			buffer.flip();
			Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
			buffer.compact();
			if (null != packet)
			{
				Packet_Chat chat = (Packet_Chat) packet;
				String expectedMessage = "Chat " + next;
				Assert.assertEquals(expectedMessage, chat.message);
				next += 1;
			}
		}
		client1.close();
		
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
		NetworkServer[] holder = new NetworkServer[1];
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			int _nextIndex = 0;
			@Override
			public void userLeft(int id)
			{
			}
			@Override
			public void userJoined(int id, String name)
			{
				// Starts ready - we can send the message now.
				holder[0].sendMessage(id, outgoing[_nextIndex]);
				_nextIndex += 1;
			}
			@Override
			public void packetReceived(int id, Packet packet)
			{
				// We don't expect this in these tests.
				Assert.fail();
			}
			@Override
			public void networkReady(int id)
			{
				if (_nextIndex < outgoing.length)
				{
					holder[0].sendMessage(id, outgoing[_nextIndex]);
					_nextIndex += 1;
				}
			}
		}, port);
		holder[0] = server;
		
		// Connect the client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		
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


	private int _runClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_AssignClientId assign = (Packet_AssignClientId) packet;
		buffer.clear();
		PacketCodec.serializeToBuffer(buffer, new Packet_SetClientName(name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		client.close();
		return assign.clientId;
	}

	private SocketChannel _connectAndHandshakeClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_AssignClientId assign = (Packet_AssignClientId) packet;
		Assert.assertNotNull(assign);
		buffer.clear();
		PacketCodec.serializeToBuffer(buffer, new Packet_SetClientName(name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		return client;
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
