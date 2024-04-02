package com.jeffdisher.october.process;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_BlockStateUpdate;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_EntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_RemoveCuboid;
import com.jeffdisher.october.net.Packet_RemoveEntity;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
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

	// Internal tick counter for tests and monitoring.
	private long _latestTickNumber;

	/**
	 * Starts a server process, listening on the given TCP port.  This call returns after the network has come up and
	 * the server has started running.
	 * Note that the instance takes ownership of CuboidLoader and will shut it down when stopped.
	 * 
	 * @param port The port on which to listen for client connections.
	 * @param millisPerTick The number of milliseconds which should be allowed to pass between logical ticks.
	 * @param cuboidLoader The loader object which will load or generate required cuboids.
	 * @param currentTimeMillisProvider The provider of the current system time, in milliseconds.
	 * @throws IOException There was an error starting up the network.
	 */
	public ServerProcess(int port
			, long millisPerTick
			, ResourceLoader cuboidLoader
			, LongSupplier currentTimeMillisProvider
	) throws IOException
	{
		_clients = new HashMap<>();
		// We will assume that ServerRunner will shut down the cuboidLoader for us.
		_server = new ServerRunner(millisPerTick, new _ServerListener(), cuboidLoader, currentTimeMillisProvider);
		// The server passes its listener back within the constructor so we should see that, now.
		Assert.assertTrue(null != _serverListener);
		_network = new NetworkServer(new _NetworkListener(), port);
	}

	public synchronized long waitForTicksToPass(long ticksToAdvance) throws InterruptedException
	{
		long target = _latestTickNumber + ticksToAdvance;
		while (_latestTickNumber < target)
		{
			this.wait();
		}
		return _latestTickNumber;
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

	private synchronized int _createClient(String name)
	{
		// For now, we will just hash the string and use that as the ID (Java's string hash is reasonable).  We still want the number to be positive, though.
		// In the future, we probably want an in-memory whitelist so this can remain synchronous.
		int hash = Math.abs(name.hashCode());
		if (_clients.containsKey(hash))
		{
			// Already present, so fail.
			hash = 0;
		}
		else
		{
			// This is valid so install it.
			_clients.put(hash, new _ClientBuffer());
			_serverListener.clientConnected(hash);
		}
		return hash;
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

	public synchronized void _updateTickNumber(long tickNumber)
	{
		_latestTickNumber = tickNumber;
		this.notifyAll();
	}


	// Note that all callbacks are issued on the NetworkServer's thread.
	private class _NetworkListener implements NetworkServer.IListener
	{
		@Override
		public int userJoined(String name)
		{
			return _createClient(name);
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
		public void removeEntity(int clientId, int entityId)
		{
			Packet_RemoveEntity packet = new Packet_RemoveEntity(entityId);
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
		public void removeCuboid(int clientId, CuboidAddress address)
		{
			Packet_RemoveCuboid packet = new Packet_RemoveCuboid(address);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendEntityUpdate(int clientId, int entityId, IEntityUpdate update)
		{
			Packet_EntityUpdateFromServer packet = new Packet_EntityUpdateFromServer(entityId, update);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendBlockUpdate(int clientId, MutationBlockSetBlock update)
		{
			Packet_BlockStateUpdate packet = new Packet_BlockStateUpdate(update);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
		{
			if (ServerRunner.FAKE_CLIENT_ID == clientId)
			{
				_updateTickNumber(tickNumber);
			}
			else
			{
				Packet_EndOfTick packet = new Packet_EndOfTick(tickNumber, latestLocalCommitIncluded);
				_bufferPacket(clientId, packet);
			}
		}
		@Override
		public void disconnectClient(int clientId)
		{
			// We just pass this along since we will handle any state update when we see the disconnected callback (so there is only one relevant path).
			_network.disconnectClient(clientId);
		}
	}


	private static class _ClientBuffer
	{
		public final Queue<Packet> outgoing = new LinkedList<>();
		public boolean isNetworkReady = false;
	}
}
