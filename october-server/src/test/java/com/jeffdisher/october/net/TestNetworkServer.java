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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.function.LongSupplier;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.net.NetworkServer.ConnectingClientDescription;


public class TestNetworkServer
{
	public static final LongSupplier TIME_SUPPLIER = () -> 1L;
	public static final long MILLIS_PER_TICK = 100L;

	@Test
	public void basicHandshakeDisconnect() throws IOException
	{
		int[] leftCount = new int[1];
		Map<Integer, String> joinNames = new HashMap<>();
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name)
			{
				int id = name.hashCode();
				Assert.assertFalse(joinNames.containsKey(id));
				joinNames.put(id, name);
				return new NetworkServer.ConnectingClientDescription<>(id, token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
				// We don't always see that the user has left if we shut down first.
				leftCount[0] += 1;
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token)
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
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		int client1 = _runClient(port, "Client 1");
		int client2 = _runClient(port, "Client 2");
		Assert.assertEquals("Client 1".hashCode(), client1);
		Assert.assertEquals("Client 2".hashCode(), client2);
		// We should see at least one disconnect, since we do these in series.
		Assert.assertTrue(leftCount[0] > 0);
		// Similarly, we will see the first client appear, since the disconnect-accept is lock-step on the server, but maybe not the second.
		Assert.assertEquals(joinNames.get(client1), "Client 1");
		
		server.stop();
	}

	@Test
	public void chat() throws IOException
	{
		// Note that we only use Packet_SendChatMessage here since it is "some message type" for the test but the server internals will use it differently.
		int port = 3000;
		@SuppressWarnings("unchecked")
		NetworkServer<NetworkLayer.PeerToken>[] holder = new NetworkServer[1];
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			private List<String> _messagesFor1 = new ArrayList<>();
			NetworkLayer.PeerToken _firstPeer = null;
			private boolean _isReady1 = false;
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name)
			{
				// If this is the first peer, hold on to it.
				if (null == _firstPeer)
				{
					_firstPeer = token;
				}
				// Starts ready.
				_isReady1 = true;
				return new NetworkServer.ConnectingClientDescription<>(name.hashCode(), token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token)
			{
				_isReady1 = true;
				_handle();
			}
			@Override
			public void networkReadReady(NetworkLayer.PeerToken token)
			{
				List<PacketFromClient> packets = holder[0].readBufferedPackets(token);
				for (Packet packet : packets)
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
				if (_isReady1 && !_messagesFor1.isEmpty())
				{
					String message = _messagesFor1.remove(0);
					_isReady1 = false;
					holder[0].sendMessage(_firstPeer, new Packet_ReceiveChatMessage(2, message));
				}
			}
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK);
		holder[0] = server;
		
		// Connect both client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		SocketChannel client2 = _connectAndHandshakeClient(port, "Client 2");
		
		// Send messages from client 2 to 1.
		for (int i = 0; i < 10; ++i)
		{
			String message = "Chat " + i;
			ByteBuffer buffer = ByteBuffer.allocate(16);
			PacketCodec.serializeToBuffer(buffer, new Packet_SendChatMessage(0, message));
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
				Packet_ReceiveChatMessage chat = (Packet_ReceiveChatMessage) packet;
				String expectedMessage = "Chat " + next;
				Assert.assertEquals(expectedMessage, chat.message);
				next += 1;
			}
		}
		client1.close();
		
		server.stop();
	}

	@Test
	public void corruptPacket() throws IOException
	{
		// Show what happens when we receive a corrupt packet from the client.
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		// Connect a client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		
		// Fill the socket with corrupt data (something which looks like a small but invalid packet):  size 0x10, ordinal 0.
		// (if this is too large, we will just wait for the rest of it).
		ByteBuffer buffer = ByteBuffer.allocate(64);
		buffer.putShort((short)0x10);
		buffer.put((byte)0);
		buffer.flip();
		// (we want to write the full buffer)
		buffer.limit(buffer.capacity());
		client1.write(buffer);
		
		// Try to read since we should see the disconnect.
		buffer.clear();
		int sizeRead = client1.read(buffer);
		Assert.assertEquals(-1, sizeRead);
		
		// Shut down.
		server.stop();
	}

	@Test
	public void deniedPacket() throws IOException
	{
		// Show what happens when we receive a "from server" packet from the client.
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		// Connect a client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		
		// We will send the "receive chat" packet, since that is only intended to be TO a client, not from one.
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		PacketCodec.serializeToBuffer(buffer, new Packet_ReceiveChatMessage(0, "message"));
		buffer.flip();
		client1.write(buffer);
		
		// Try to read since we should see the disconnect.
		buffer.clear();
		int sizeRead = client1.read(buffer);
		Assert.assertEquals(-1, sizeRead);
		
		// Shut down.
		server.stop();
	}

	@Test
	public void deniedMutation() throws IOException
	{
		// Show what happens when we receive a restricted mutation from a client.
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		// Connect a client.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		
		// We will send EntityChangePeriodic, since it is internal only, but can be serialized.
		// (this requires that we manually build the buffer, since the code normally rejects this).
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.position(PacketCodec.HEADER_BYTES);
		MutationEntityCodec.serializeToBuffer(buffer, new EntityChangePeriodic());
		buffer.putLong(1L);
		int size = buffer.position();
		
		buffer.position(0);
		buffer.putShort((short)size);
		buffer.put((byte) Packet_MutationEntityFromClient.TYPE.ordinal());
		buffer.position(size);
		
		buffer.flip();
		client1.write(buffer);
		
		// Try to read since we should see the disconnect.
		buffer.clear();
		int sizeRead = client1.read(buffer);
		Assert.assertEquals(-1, sizeRead);
		
		// Shut down.
		server.stop();
	}

	@Test
	public void timeoutOnHandshake() throws Throwable
	{
		// Show what happens when we wait for too long before sending the initial handshake message.
		long[] time = new long[] {1L};
		CountDownLatch latch = new CountDownLatch(1);
		LongSupplier timeSupplier = () -> {
			// This request will race against the main thread so use a latch to make sure that the first time has been requested.
			long timeToReturn = time[0];;
			latch.countDown();
			return timeToReturn;
		};
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), timeSupplier, port, MILLIS_PER_TICK);
		
		// Open a socket.
		SocketChannel socket = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		latch.await();
		
		// Advance time for the timeout.
		time[0] += NetworkServer.DEFAULT_NEW_CONNECTION_TIMEOUT_MILLIS;
		
		// Now connect the client, since that should cause the timeout to be checked.
		SocketChannel client1 = _connectAndHandshakeClient(port, "Client 1");
		
		// Verify that reading the socket shows it is closed.
		ByteBuffer buffer = ByteBuffer.allocate(64);
		int read = socket.read(buffer);
		Assert.assertEquals(-1, read);
		
		// Now, just close the other connection.
		client1.close();
		
		// Shut down.
		server.stop();
	}

	@Test
	public void unknownVersion() throws Throwable
	{
		// We will check what happens when we pass an unknown version on connect.
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		// Connect a client.
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the first step of the handshake.
		int bogusVersion = Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION + 1;
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientSendDescription(bogusVersion, "version fail"));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Verify that reading the socket shows it is closed.
		buffer = ByteBuffer.allocate(64);
		int read = client.read(buffer);
		Assert.assertEquals(-1, read);
		
		// Shut down.
		server.stop();
	}

	@Test
	public void heavyThreadSaturation() throws Throwable
	{
		// We will start 100 threads and make them all attempt a basic connect and handshake at the same time to see that they all make it through.
		int threadCount = 100;
		int[] joinLeaveCounts = new int[2];
		CountDownLatch closeLatch = new CountDownLatch(threadCount);
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new NetworkServer.IListener<>()
		{
			@Override
			public NetworkServer.ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name)
			{
				joinLeaveCounts[0] += 1;
				return new NetworkServer.ConnectingClientDescription<>(joinLeaveCounts[0], token);
			}
			@Override
			public void userLeft(NetworkLayer.PeerToken token)
			{
				joinLeaveCounts[1] += 1;
				closeLatch.countDown();
			}
			@Override
			public void networkWriteReady(NetworkLayer.PeerToken token)
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
		}, TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		CyclicBarrier connectBarrier = new CyclicBarrier(threadCount);
		CyclicBarrier endBarrier = new CyclicBarrier(threadCount);
		Thread[] clients = new Thread[threadCount];
		for (int i = 0; i < threadCount; ++i)
		{
			String name = "Client " + i;
			clients[i] = new Thread(() -> {
				try
				{
					connectBarrier.await();
					SocketChannel client = _connectAndHandshakeClient(port, name);
					endBarrier.await();
					client.close();
				}
				catch (Throwable t)
				{
					t.printStackTrace();
					// If this error happens, we want to see it.
					System.exit(1);
				}
			});
		}
		for (int i = 0; i < threadCount; ++i)
		{
			clients[i].start();
		}
		for (int i = 0; i < threadCount; ++i)
		{
			clients[i].join();
		}
		closeLatch.await();
		server.stop();
		
		Assert.assertEquals(threadCount, joinLeaveCounts[0]);
		Assert.assertEquals(threadCount, joinLeaveCounts[1]);
	}

	@Test
	public void pollStatus() throws Throwable
	{
		// Just poll for status.
		int port = 3000;
		NetworkServer<NetworkLayer.PeerToken> server = new NetworkServer<>(new _ServerListener(), TIME_SUPPLIER, port, MILLIS_PER_TICK);
		
		// Connect a client.
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the poll.
		long millis = 5L;
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientPollServerStatus(Packet_ClientPollServerStatus.NETWORK_POLL_VERSION, millis));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Verify that we get a response packet and that the connection is closed.
		buffer = ByteBuffer.allocate(64);
		int read = client.read(buffer);
		buffer.flip();
		Packet response = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_ServerReturnServerStatus safe = (Packet_ServerReturnServerStatus)response;
		Assert.assertEquals(Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION, safe.version);
		Assert.assertEquals("Name", safe.serverName);
		Assert.assertEquals(42, safe.clientCount);
		Assert.assertEquals(millis, safe.millis);
		
		// We should now see disconnect.
		buffer.clear();
		read = client.read(buffer);
		Assert.assertEquals(-1, read);
		
		// Shut down.
		server.stop();
	}


	private int _runClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the first step of the handshake.
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientSendDescription(Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION, name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Now read the response.
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_ServerSendClientId assign = (Packet_ServerSendClientId) packet;
		client.close();
		return assign.clientId;
	}

	private SocketChannel _connectAndHandshakeClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the first step of the handshake.
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientSendDescription(Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION, name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Now read the response.
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_ServerSendClientId assign = (Packet_ServerSendClientId) packet;
		Assert.assertNotNull(assign);
		return client;
	}


	private static class _ServerListener implements NetworkServer.IListener<NetworkLayer.PeerToken>
	{
		@Override
		public ConnectingClientDescription<NetworkLayer.PeerToken> userJoined(NetworkLayer.PeerToken token, String name)
		{
			return new NetworkServer.ConnectingClientDescription<>(name.hashCode(), token);
		}
		@Override
		public void userLeft(NetworkLayer.PeerToken data)
		{
		}
		@Override
		public void networkWriteReady(NetworkLayer.PeerToken data)
		{
		}
		@Override
		public void networkReadReady(NetworkLayer.PeerToken data)
		{
		}
		@Override
		public NetworkServer.ServerStatus pollServerStatus()
		{
			return new NetworkServer.ServerStatus("Name", 42);
		}
	}
}
