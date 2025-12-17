package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.function.LongSupplier;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.OctreeInflatedByte;
import com.jeffdisher.october.data.OctreeObject;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.net.CuboidCodec;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.net.PollingClient;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.net.Packet_ReceiveChatMessage;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestIntegratedNetwork
{
	public static final LongSupplier TIME_SUPPLIER = () -> 1L;
	public static final long MILLIS_PER_TICK = 100L;

	@BeforeClass
	public static void setup()
	{
		Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicHandshakeDisconnect() throws Throwable
	{
		int[] leftCount = new int[1];
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			Map<Integer, String> _joinNames = new HashMap<>();
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
			{
				// We might not see these if we connect/disconnect too quickly but we shouldn't see duplication.
				int id = name.hashCode();
				Assert.assertFalse(_joinNames.containsKey(id));
				_joinNames.put(id, name);
				return new NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken>(id, token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
				// We don't always see that the user has left if we shut down first.
				leftCount[0] += 1;
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token, ByteBuffer bufferToWrite)
			{
				// We aren't acting on this in our test.
			}
			@Override
			public void networkReadReady(NetworkLayer.PeerToken token)
			{
				// Should not happen in this test.
				Assert.fail();
			}
			@Override
			public NetworkServer.ServerStatus pollServerStatus()
			{
				// Should not happen in this test.
				Assert.fail();
				return null;
			}
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		
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
		// Note that we only use Packet_SendChatMessage here since it is "some message type" for the test but the server internals will use it differently.
		int port = 3000;
		@SuppressWarnings("unchecked")
		NetworkServer<NetworkLayer.PeerToken>[] holder = new NetworkServer[1];
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			private List<String> _messagesFor1 = new ArrayList<>();
			NetworkLayer.PeerToken _firstPeer = null;
			private ByteBuffer _bufferToWrite = null;
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
			{
				// If this is the first peer, hold on to it.
				if (null == _firstPeer)
				{
					_firstPeer = token;
				}
				return new NetworkServer.ConnectingClientDescription<>(name.hashCode(), token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token, ByteBuffer bufferToWrite)
			{
				_bufferToWrite = bufferToWrite;
				_handle();
			}
			@Override
			public void networkReadReady(NetworkLayer.PeerToken token)
			{
				List<PacketFromClient> packets = holder[0].readBufferedPackets(token);
				for (PacketFromClient packet : packets)
				{
					// We only expect chat messages.
					Packet_SendChatMessage chat = (Packet_SendChatMessage) packet;
					_messagesFor1.add(chat.message);
					_handle();
				}
			}
			@Override
			public NetworkServer.ServerStatus pollServerStatus()
			{
				// Should not happen in this test.
				Assert.fail();
				return null;
			}
			private void _handle()
			{
				if ((null != _bufferToWrite) && !_messagesFor1.isEmpty())
				{
					String message = _messagesFor1.remove(0);
					PacketCodec.serializeToBuffer(_bufferToWrite, new Packet_ReceiveChatMessage("Client 2".hashCode(), message));
					_bufferToWrite.flip();
					ByteBuffer toSend = _bufferToWrite;
					_bufferToWrite = null;
					holder[0].sendBuffer(_firstPeer, toSend);
				}
			}
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		holder[0] = server;
		
		// Connect both clients.
		_Handoff<Packet> handoff = new _Handoff<>();
		NetworkClient client1 = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum)
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
		}, InetAddress.getLocalHost(), port, "Client 1", 1);
		CyclicBarrier readyBarrier = new CyclicBarrier(2);
		NetworkClient client2 = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum)
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
		}, InetAddress.getLocalHost(), port, "Client 2", 1);
		
		// Send messages from client 2 to 1.
		for (int i = 0; i < 10; ++i)
		{
			readyBarrier.await();
			String message = "Chat " + i;
			client2.sendMessage(new Packet_SendChatMessage(0, message));
		}
		// Wait for this to finish sending.
		readyBarrier.await();
		client2.stop();
		
		// Read the messages from client 1.
		for (int i = 0; i < 10; ++i)
		{
			Packet packet = handoff.load();
			Packet_ReceiveChatMessage chat = (Packet_ReceiveChatMessage) packet;
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
		for (byte x = 0; x < 32; ++x)
		{
			for (byte y = 0; y < 32; ++y)
			{
				for (byte z = 0; z < 32; ++z)
				{
					octree.setData(new BlockAddress(x, y, z), value);
					value += 1;
				}
			}
		}
		// Combine this into a basic cuboid.
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidData.createNew(cuboidAddress, new IOctree[] { octree
				, OctreeObject.create()
				, OctreeShort.create((short)0)
				, OctreeObject.create()
				, OctreeObject.create()
				, OctreeInflatedByte.empty()
				, OctreeInflatedByte.empty()
				, OctreeInflatedByte.empty()
				, OctreeInflatedByte.empty()
				, OctreeObject.create()
				, OctreeObject.create()
				, OctreeObject.create()
		});
		
		// We should be able to send this as 1 start packet and 2 fragment packets.
		CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(cuboid);
		Packet_CuboidStart start = (Packet_CuboidStart) serializer.getNextPacket();
		Packet_CuboidFragment frag1 = (Packet_CuboidFragment) serializer.getNextPacket();
		Packet_CuboidFragment frag2 = (Packet_CuboidFragment) serializer.getNextPacket();
		Assert.assertNull(serializer.getNextPacket());
		PacketFromServer[] outgoing = new PacketFromServer[] { start, frag1, frag2 };
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		@SuppressWarnings("unchecked")
		NetworkServer<NetworkLayer.PeerToken>[] holder = new NetworkServer[1];
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			int _nextIndex = 0;
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
			{
				// We will sent the message once the network is ready.
				return new NetworkServer.ConnectingClientDescription<>(name.hashCode(), token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token, ByteBuffer bufferToWrite)
			{
				if (_nextIndex < outgoing.length)
				{
					PacketCodec.serializeToBuffer(bufferToWrite, outgoing[_nextIndex]);
					bufferToWrite.flip();
					holder[0].sendBuffer(token, bufferToWrite);
					_nextIndex += 1;
				}
			}
			@Override
			public void networkReadReady(NetworkLayer.PeerToken token)
			{
				// We don't expect this in these tests.
				Assert.fail();
			}
			@Override
			public NetworkServer.ServerStatus pollServerStatus()
			{
				// Should not happen in this test.
				Assert.fail();
				return null;
			}
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		holder[0] = server;
		
		// Connect the client.
		Packet[] received = new Packet[3];
		CountDownLatch latch = new CountDownLatch(1);
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			int _nextIndex = 0;
			@Override
			public void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum)
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
		}, InetAddress.getLocalHost(), port, "test", 1);
		
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
		Assert.assertEquals(0, finished.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0)));
		Assert.assertEquals((32 * 32 * 10) + (32 * 10) + 10, finished.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 10)));
		Assert.assertEquals((32 * 32 * 30) + (32 * 30) + 30, finished.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 30)));
	}

	@Test
	public void pollNetwork() throws Throwable
	{
		int threadCount = Thread.activeCount();
		int port0 = 3000;
		int port1 = 3001;
		NetworkServer<NetworkLayer.PeerToken> server0 = _pollOnlyServer(port0);
		NetworkServer<NetworkLayer.PeerToken> server1 = _pollOnlyServer(port1);
		
		CountDownLatch statusLatch = new CountDownLatch(4);
		CountDownLatch timeoutLatch = new CountDownLatch(2);
		PollingClient client = new PollingClient(new PollingClient.IListener()
		{
			@Override
			public void serverReturnedStatus(InetSocketAddress serverToPoll, int version, String serverName, int clientCount, long millisDelay)
			{
				statusLatch.countDown();
			}
			@Override
			public void networkTimeout(InetSocketAddress serverToPoll)
			{
				timeoutLatch.countDown();
			}
		});
		// Poll both and something invalid.
		client.pollServer(new InetSocketAddress("127.0.0.1", port0));
		client.pollServer(new InetSocketAddress("127.0.0.1", port1));
		client.pollServer(new InetSocketAddress("127.0.0.1", 4000));
		client.pollServer(new InetSocketAddress("127.0.0.1", port0));
		client.pollServer(new InetSocketAddress("127.0.0.1", port1));
		client.pollServer(new InetSocketAddress("127.0.0.1", 4000));
		statusLatch.await();
		timeoutLatch.await();
		
		client.shutdown();
		server0.stop();
		server1.stop();
		int endThreadCount = Thread.activeCount();
		Assert.assertEquals(threadCount, endThreadCount);
	}


	private int _runClient(int port, String name) throws Throwable
	{
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			@Override
			public void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum)
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
		}, InetAddress.getLocalHost(), port, name, 1);
		int clientId = client.getClientId();
		client.stop();
		return clientId;
	}

	private NetworkServer<NetworkLayer.PeerToken> _pollOnlyServer(int port) throws IOException
	{
		return new NetworkServer<>(new NetworkServer.IListener<>()
		{
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
			{
				throw new AssertionError("Not called");
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
				throw new AssertionError("Not called");
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token, ByteBuffer bufferToWrite)
			{
				throw new AssertionError("Not called");
			}
			@Override
			public void networkReadReady(NetworkLayer.PeerToken token)
			{
				throw new AssertionError("Not called");
			}
			@Override
			public NetworkServer.ServerStatus pollServerStatus()
			{
				return new NetworkServer.ServerStatus("Server", 42);
			}
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK, MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
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
