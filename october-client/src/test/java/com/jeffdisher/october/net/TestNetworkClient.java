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

import com.jeffdisher.october.data.OctreeShort;
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
		byte[] rawData = octree.copyRawData();
		// Get the fragments.
		Packet_CuboidSetAspectShortPart[] fragments = Packet_CuboidSetAspectShortPart.fragmentData(new CuboidAddress((short)0, (short)0, (short)0), 0, rawData);
		// We expect this to be split.
		Assert.assertEquals(2, fragments.length);
		
		// Now, create a server, connect a client to it, and send the data to the client and make sure it arrives correctly.
		int port = 3000;
		InetSocketAddress address = new InetSocketAddress(port);
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(address);
		
		// Connect the client.
		Packet_CuboidSetAspectShortPart[] received = new Packet_CuboidSetAspectShortPart[2];
		CountDownLatch latch = new CountDownLatch(1);
		NetworkClient[] holder = new NetworkClient[1];
		NetworkClient client = new NetworkClient(new NetworkClient.IListener()
		{
			boolean _sent = false;
			@Override
			public void networkReady()
			{
				if (!_sent)
				{
					holder[0].sendMessage(fragments[1]);
					_sent = true;
				}
			}
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
		}, InetAddress.getLocalHost(), port);
		holder[0] = client;
		SocketChannel connection = _serverHandshake(server, 1);
		
		// Send these from the server and wait to see them on the client side.
		_sendServerMessage(connection, fragments[0]);
		_sendServerMessage(connection, fragments[1]);
		latch.await();
		
		byte[] stitched = Packet_CuboidSetAspectShortPart.stitchPlanes(new Packet_CuboidSetAspectShortPart[] {
				received[0],
				received[1],
		});
		Assert.assertArrayEquals(rawData, stitched);
		
		client.stop();
		connection.close();
		server.close();
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
