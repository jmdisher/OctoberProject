package com.jeffdisher.october.process;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_MutationBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromServer;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.utils.Assert;


/**
 * Meant to be a more-or-less self-contained server process.  It internally runs NetworkServer and ServerRunner.
 */
public class ServerProcess
{
	private final Map<Integer, _ClientBuffer> _clients;
	private final ServerRunner _server;
	private final NetworkServer _network;

	private IServerAdapter.IListener _serverListener;

	/**
	 * Starts a server process, listening on the given TCP port.  This call returns after the network has come up and
	 * the server has started running.
	 * 
	 * @param port The port on which to listen for client connections.
	 * @param millisPerTick The number of milliseconds which should be allowed to pass between logical ticks.
	 * @param currentTimeMillisProvider The provider of the current system time, in milliseconds.
	 * @throws IOException There was an error starting up the network.
	 */
	public ServerProcess(int port, long millisPerTick, LongSupplier currentTimeMillisProvider) throws IOException
	{
		_clients = new HashMap<>();
		_server = new ServerRunner(millisPerTick, new _ServerListener(), currentTimeMillisProvider);
		// The server passes its listener back within the constructor so we should see that, now.
		Assert.assertTrue(null != _serverListener);
		_network = new NetworkServer(new _NetworkListener(), port);
	}

	/**
	 * Tells the server to load in the given cuboid.
	 * 
	 * @param cuboid The cuboid to inject into the server.
	 */
	public void loadCuboid(CuboidData cuboid)
	{
		_server.loadCuboid(cuboid);
	}

	/**
	 * Stops the server and network, returning when everything is shut down.
	 */
	public void stop()
	{
		_server.shutdown();
		_network.stop();
	}


	private synchronized void _serverReady(IServerAdapter.IListener serverListener)
	{
		Assert.assertTrue(null == _serverListener);
		_serverListener = serverListener;
		this.notifyAll();
	}

	private synchronized void _createClient(int clientId)
	{
		_clients.put(clientId, new _ClientBuffer());
		_serverListener.clientConnected(clientId);
	}

	private synchronized void _destroyClient(int clientId)
	{
		_serverListener.clientDisconnected(clientId);
		_clients.remove(clientId);
	}

	private synchronized void _sendNextPacket(int clientId)
	{
		_ClientBuffer buffer = _clients.get(clientId);
		if (buffer.outgoing.isEmpty())
		{
			Assert.assertTrue(!buffer.isNetworkReady);
			buffer.isNetworkReady = true;
		}
		else
		{
			Packet next = buffer.outgoing.poll();
			_network.sendMessage(clientId, next);
			buffer.isNetworkReady = false;
		}
	}

	private synchronized void _bufferPacket(int clientId, Packet packet)
	{
		_ClientBuffer buffer = _clients.get(clientId);
		if (buffer.isNetworkReady)
		{
			_network.sendMessage(clientId, packet);
			buffer.isNetworkReady = false;
		}
		else
		{
			buffer.outgoing.add(packet);
		}
	}


	// Note that all callbacks are issued on the NetworkServer's thread.
	private class _NetworkListener implements NetworkServer.IListener
	{
		@Override
		public void userJoined(int id, String name)
		{
			_createClient(id);
		}
		@Override
		public void userLeft(int id)
		{
			_destroyClient(id);
		}
		@Override
		public void networkReady(int id)
		{
			_sendNextPacket(id);
		}
		@Override
		public void packetReceived(int id, Packet packet)
		{
			// The only packets which are currently sent after the handshake are entity mutations.
			Packet_MutationEntityFromClient safe = (Packet_MutationEntityFromClient) packet;
			_serverListener.changeReceived(id, safe.mutation, safe.commitLevel);
		}
	}

	private class _ServerListener implements IServerAdapter
	{
		@Override
		public void readyAndStartListening(IServerAdapter.IListener listener)
		{
			// We can store this and use it to plumb calls into the server.
			_serverReady(listener);
		}
		@Override
		public void sendEntity(int clientId, Entity entity)
		{
			Packet_Entity packet = new Packet_Entity(entity);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			// Serialize the entire cuboid.
			// Note that this may be too expensive to do on the server's thread.
			CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(cuboid);
			Packet packet = serializer.getNextPacket();
			while (null != packet)
			{
				_bufferPacket(clientId, packet);
				packet = serializer.getNextPacket();
			}
		}
		@Override
		public void sendChange(int clientId, int entityId, IMutationEntity change)
		{
			Packet_MutationEntityFromServer packet = new Packet_MutationEntityFromServer(entityId, change);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendMutation(int clientId, IMutationBlock mutation)
		{
			Packet_MutationBlock packet = new Packet_MutationBlock(mutation);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
		{
			// We ignore calls to the fake client in this path.
			if (ServerRunner.FAKE_CLIENT_ID != clientId)
			{
				Packet_EndOfTick packet = new Packet_EndOfTick(tickNumber, latestLocalCommitIncluded);
				_bufferPacket(clientId, packet);
			}
		}
		@Override
		public void disconnectClient(int clientId)
		{
			// TODO:  Implement.
			throw new AssertionError("Not implemented");
		}
	}


	private static class _ClientBuffer
	{
		public final Queue<Packet> outgoing = new LinkedList<>();
		public boolean isNetworkReady = true;
	}
}
