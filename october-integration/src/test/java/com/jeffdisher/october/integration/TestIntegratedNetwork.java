package com.jeffdisher.october.integration;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_Chat;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestIntegratedNetwork
{
	@Test
	public void basicHandshakeDisconnect() throws Throwable
	{
		int[] leftCount = new int[1];
		int port = 3000;
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			Map<Integer, String> _joinNames = new HashMap<>();
			@Override
			public int userJoined(String name)
			{
				// We might not see these if we connect/disconnect too quickly but we shouldn't see duplication.
				int id = name.hashCode();
				Assert.assertFalse(_joinNames.containsKey(id));
				_joinNames.put(id, name);
				return id;
			}
			@Override
			public void userLeft(int id)
			{
				// We don't always see that the user has left if we shut down first.
				leftCount[0] += 1;
			}
			@Override
			public void networkReady(int id)
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void packetReceived(int id, Packet packet)
			{
				// Should not happen in this test.
				Assert.fail();
			}
		}, port);
		
		int client1 = _runClient(port, "Client 1");
		int client2 = _runClient(port, "Client 2");
		Assert.assertEquals("Client 1".hashCode(), client1);
		Assert.assertEquals("Client 2".hashCode(), client2);
		// We should see at least one disconnect, since we do these in series.
		Assert.assertTrue(leftCount[0] > 0);
		
		server.stop();
	}

	@Test
	public void chat() throws Throwable
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
			public int userJoined(String name)
			{
				// Starts ready.
				_isReady1 = true;
				return name.hashCode();
			}
			@Override
			public void packetReceived(int id, Packet packet)
			{
				// We only expect chat messages.
				Packet_Chat chat = (Packet_Chat) packet;
				Assert.assertEquals("Client 2".hashCode(), id);
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
					holder[0].sendMessage("Client 1".hashCode(), new Packet_Chat("Client 2".hashCode(), message));
				}
			}
		}, port);
		holder[0] = server;
		
		// Connect both clients.
		_Handoff<Packet> handoff = new _Handoff<>();
		NetworkClient client1 = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
			}
			@Override
			public void packetReceived(Packet packet)
			{
				handoff.store(packet);
			}
			@Override
			public void networkReady()
			{
			}
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		}, InetAddress.getLocalHost(), port, "Client 1");
		CyclicBarrier readyBarrier = new CyclicBarrier(2);
		NetworkClient client2 = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
			}
			@Override
			public void packetReceived(Packet packet)
			{
			}
			@Override
			public void networkReady()
			{
				try
				{
					readyBarrier.await();
				}
				catch (InterruptedException | BrokenBarrierException e)
				{
					throw new AssertionError("Not expected in test", e);
				}
			}
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		}, InetAddress.getLocalHost(), port, "Client 2");
		
		// Send messages from client 2 to 1.
		for (int i = 0; i < 10; ++i)
		{
			readyBarrier.await();
			String message = "Chat " + i;
			client2.sendMessage(new Packet_Chat(0, message));
		}
		// Wait for this to finish sending.
		readyBarrier.await();
		client2.stop();
		
		// Read the messages from client 1.
		for (int i = 0; i < 10; ++i)
		{
			Packet packet = handoff.load();
			Packet_Chat chat = (Packet_Chat) packet;
			String expectedMessage = "Chat " + i;
			Assert.assertEquals(expectedMessage, chat.message);
		}
		client1.stop();
		
		server.stop();
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
		CuboidData cuboid = CuboidData.createNew(cuboidAddress, new IOctree[] { octree
				, OctreeObject.create()
				, OctreeShort.create((short)0)
				, OctreeObject.create()
				, OctreeObject.create()
				, OctreeByte.create((byte)0)
		});
		
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
			public int userJoined(String name)
			{
				// We will sent the message once the network is ready.
				return name.hashCode();
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
		Packet[] received = new Packet[3];
		CountDownLatch latch = new CountDownLatch(1);
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			int _nextIndex = 0;
			@Override
			public void handshakeCompleted(int assignedId)
			{
			}
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
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		}, InetAddress.getLocalHost(), port, "test");
		
		// Wait to receive these and then stitch them together.
		latch.await();
		
		client.stop();
		server.stop();
		
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


	private int _runClient(int port, String name) throws Throwable
	{
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId)
			{
			}
			@Override
			public void packetReceived(Packet packet)
			{
			}
			@Override
			public void networkReady()
			{
			}
			@Override
			public void serverDisconnected()
			{
				// Should not happen in this test.
				Assert.fail();
			}
		}, InetAddress.getLocalHost(), port, name);
		int clientId = client.getClientId();
		client.stop();
		return clientId;
	}


	private static class _Handoff<T>
	{
		// This is used in tests to allow a blocking hand-off between 2 threads.
		private T _value;
		public synchronized void store(T value)
		{
			while (null != _value)
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					throw new AssertionError("Not expected in test", e);
				}
			}
			_value = value;
			this.notifyAll();
		}
		public synchronized T load()
		{
			while (null == _value)
			{
				try
				{
					this.wait();
				}
				catch (InterruptedException e)
				{
					throw new AssertionError("Not expected in test", e);
				}
			}
			T val = _value;
			_value = null;
			this.notifyAll();
			return val;
		}
	}
}
