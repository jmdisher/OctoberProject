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
			public int userJoined(NetworkLayer.PeerToken token, String name)
			{
				int id = name.hashCode();
				Assert.assertFalse(joinNames.containsKey(id));
				joinNames.put(id, name);
				return id;
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
		}, port);
		
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
		int port = 3000;
		NetworkServer[] holder = new NetworkServer[1];
		NetworkServer server = new NetworkServer(new NetworkServer.IListener()
		{
			private List<String> _messagesFor1 = new ArrayList<>();
			NetworkLayer.PeerToken _firstPeer = null;
			private boolean _isReady1 = false;
			@Override
			public int userJoined(NetworkLayer.PeerToken token, String name)
			{
				// If this is the first peer, hold on to it.
				if (null == _firstPeer)
				{
					_firstPeer = token;
				}
				// Starts ready.
				_isReady1 = true;
				return name.hashCode();
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
				List<Packet> packets = holder[0].readBufferedPackets(token);
				for (Packet packet : packets)
				{
					// We only expect chat messages.
					Packet_Chat chat = (Packet_Chat) packet;
					_messagesFor1.add(chat.message);
					_handle();
				}
			}
			private void _handle()
			{
				if (_isReady1 && !_messagesFor1.isEmpty())
				{
					String message = _messagesFor1.remove(0);
					_isReady1 = false;
					holder[0].sendMessage(_firstPeer, new Packet_Chat(2, message));
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


	private int _runClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the first step of the handshake.
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientSendDescription(0, name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Now read the response.
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_ServerSendConfiguration assign = (Packet_ServerSendConfiguration) packet;
		client.close();
		return assign.clientId;
	}

	private SocketChannel _connectAndHandshakeClient(int port, String name) throws IOException, UnknownHostException
	{
		SocketChannel client = SocketChannel.open(new InetSocketAddress(InetAddress.getLocalHost(), port));
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		
		// Send the first step of the handshake.
		PacketCodec.serializeToBuffer(buffer, new Packet_ClientSendDescription(0, name));
		buffer.flip();
		client.write(buffer);
		Assert.assertFalse(buffer.hasRemaining());
		
		// Now read the response.
		buffer.clear();
		client.read(buffer);
		buffer.flip();
		Packet packet = PacketCodec.parseAndSeekFlippedBuffer(buffer);
		Packet_ServerSendConfiguration assign = (Packet_ServerSendConfiguration) packet;
		Assert.assertNotNull(assign);
		return client;
	}
}
