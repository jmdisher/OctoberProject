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

import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_Chat;
import com.jeffdisher.october.net.Packet_CuboidSetAspectShortPart;
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
			public void userJoined(int id, String name)
			{
				// We might not see these if we connect/disconnect too quickly but we shouldn't see duplication.
				Assert.assertFalse(_joinNames.containsKey(id));
				_joinNames.put(id, name);
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
		Assert.assertEquals(1, client1);
		Assert.assertEquals(2, client2);
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
		
		// Connect both clients.
		_Handoff<Packet> handoff = new _Handoff<>();
		NetworkClient client1 = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void packetReceived(Packet packet)
			{
				handoff.store(packet);
			}
			@Override
			public void networkReady()
			{
			}
		}, InetAddress.getLocalHost(), port);
		CyclicBarrier readyBarrier = new CyclicBarrier(2);
		NetworkClient client2 = new NetworkClient(new NetworkClient.IListener()
		{
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
		}, InetAddress.getLocalHost(), port);
		
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
		byte[] rawData = octree.copyRawData();
		// Get the fragments.
		Packet_CuboidSetAspectShortPart[] fragments = Packet_CuboidSetAspectShortPart.fragmentData(new CuboidAddress((short)0, (short)0, (short)0), 0, rawData);
		// We expect this to be split.
		Assert.assertEquals(2, fragments.length);
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		NetworkServer[] holder = new NetworkServer[1];
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			boolean _sent = false;
			@Override
			public void userLeft(int id)
			{
			}
			@Override
			public void userJoined(int id, String name)
			{
				// Starts ready - we can send the message now.
				holder[0].sendMessage(id, fragments[0]);
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
				if (!_sent)
				{
					holder[0].sendMessage(id, fragments[1]);
					_sent = true;
				}
			}
		}, port);
		holder[0] = server;
		
		// Connect the client.
		Packet_CuboidSetAspectShortPart[] received = new Packet_CuboidSetAspectShortPart[2];
		CountDownLatch latch = new CountDownLatch(1);
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void packetReceived(Packet packet)
			{
				Assert.assertNull(received[1]);
				if (null == received[0])
				{
					received[0] = (Packet_CuboidSetAspectShortPart)packet;
				}
				else
				{
					received[1] = (Packet_CuboidSetAspectShortPart)packet;
					latch.countDown();
				}
			}
			@Override
			public void networkReady()
			{
			}
		}, InetAddress.getLocalHost(), port);
		
		// Wait to receive these and then stitch them together.
		latch.await();
		byte[] stitched = Packet_CuboidSetAspectShortPart.stitchPlanes(new Packet_CuboidSetAspectShortPart[] {
				received[0],
				received[1],
		});
		Assert.assertArrayEquals(rawData, stitched);
		
		client.stop();
		server.stop();
	}


	private int _runClient(int port, String name) throws Throwable
	{
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void packetReceived(Packet packet)
			{
			}
			@Override
			public void networkReady()
			{
			}
		}, InetAddress.getLocalHost(), port);
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
