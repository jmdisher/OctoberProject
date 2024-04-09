package com.jeffdisher.october.process;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_BlockStateUpdate;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_PartialEntity;
import com.jeffdisher.october.net.Packet_EntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_RemoveCuboid;
import com.jeffdisher.october.net.Packet_RemoveEntity;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Meant to be a more-or-less self-contained server process.  It internally runs NetworkServer and ServerRunner.
 */
public class ServerProcess
{
	private final Map<Integer, _ClientBuffer> _clientsById;
	private final ServerRunner _server;
	private final NetworkServer<_ClientBuffer> _network;

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
		_clientsById = new HashMap<>();
		// We will assume that ServerRunner will shut down the cuboidLoader for us.
		_server = new ServerRunner(millisPerTick, new _ServerListener(), cuboidLoader, currentTimeMillisProvider);
		// The server passes its listener back within the constructor so we should see that, now.
		Assert.assertTrue(null != _serverListener);
		_network = new NetworkServer<_ClientBuffer>(new _NetworkListener(), port);
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

	private synchronized NetworkServer.ConnectingClientDescription<_ClientBuffer> _createClient(NetworkLayer.PeerToken token, String name)
	{
		// For now, we will just hash the string and use that as the ID (Java's string hash is reasonable).  We still want the number to be positive, though.
		// In the future, we probably want an in-memory whitelist so this can remain synchronous.
		NetworkServer.ConnectingClientDescription<_ClientBuffer> result;
		int hash = Math.abs(name.hashCode());
		if (0 == hash)
		{
			// This is a corner-case but we never want a non-negative value so we work around 0 here.
			hash = 1;
		}
		if (_clientsById.containsKey(hash))
		{
			// Already present, so fail.
			result = null;
		}
		else
		{
			// This is valid so install it.
			_ClientBuffer buffer = new _ClientBuffer(token, hash);
			_clientsById.put(hash, buffer);
			_serverListener.clientConnected(hash);
			result = new NetworkServer.ConnectingClientDescription<>(hash, buffer);
		}
		return result;
	}

	private synchronized void _destroyClient(_ClientBuffer buffer)
	{
		_serverListener.clientDisconnected(buffer.clientId);
		_clientsById.remove(buffer.clientId);
	}

	private synchronized void _sendNextPacket(_ClientBuffer buffer)
	{
		if (buffer.outgoing.isEmpty())
		{
			Assert.assertTrue(!buffer.isNetworkWriteable);
			buffer.isNetworkWriteable = true;
		}
		else
		{
			Packet next = buffer.outgoing.poll();
			_network.sendMessage(buffer.token, next);
			buffer.isNetworkWriteable = false;
		}
	}

	private synchronized void _bufferPacket(int clientId, Packet packet)
	{
		_ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			System.out.println("Warning: Ignoring buffer packet to client " + clientId);
		}
		else if (buffer.isNetworkWriteable)
		{
			_network.sendMessage(buffer.token, packet);
			buffer.isNetworkWriteable = false;
		}
		else
		{
			buffer.outgoing.add(packet);
		}
	}

	private synchronized void _sendDisconnectRequest(int clientId)
	{
		_ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			System.out.println("Warning: Ignoring disconnect request to client " + clientId);
		}
		else
		{
			_network.disconnectClient(buffer.token);
		}
	}

	public synchronized void _updateTickNumber(long tickNumber)
	{
		_latestTickNumber = tickNumber;
		this.notifyAll();
	}

	private synchronized void _setClientReadable(_ClientBuffer buffer)
	{
		// This can only be called if we aren't already readable.
		Assert.assertTrue(!buffer.isNetworkReadable);
		buffer.isNetworkReadable = true;
		
		// We need to notify the listener if there isn't already some readable content buffered here.
		if (buffer.incoming.isEmpty())
		{
			_serverListener.clientReadReady(buffer.clientId);
		}
	}

	private synchronized Packet _peekOrRemoveNextMutationFromClient(int clientId, Packet toRemove)
	{
		Packet packet;
		_ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			System.out.println("Warning: Ignoring peek next mutation from client " + clientId);
			packet = null;
		}
		else
		{
			// This check is unusually specific but a null toRemove is only passed if this is the first call, meaning this must be readable.
			if (null == toRemove)
			{
				Assert.assertTrue(buffer.isNetworkReadable || !buffer.incoming.isEmpty());
			}
			
			// See if we need to pull more from the lower level.
			if (buffer.incoming.isEmpty() && buffer.isNetworkReadable)
			{
				List<Packet> list = _network.readBufferedPackets(buffer.token);
				// This can't be empty if we were told that this was readable.
				Assert.assertTrue(!list.isEmpty());
				buffer.incoming.addAll(list);
				buffer.isNetworkReadable = false;
			}
			
			// Now, handle this operation.
			if (!buffer.incoming.isEmpty())
			{
				if (null != toRemove)
				{
					Packet removed = buffer.incoming.poll();
					// These must match since this is the point of toRemove.
					Assert.assertTrue(toRemove == removed);
				}
			}
			packet = buffer.incoming.peek();
		}
		return packet;
	}


	// Note that all callbacks are issued on the NetworkServer's thread.
	private class _NetworkListener implements NetworkServer.IListener<_ClientBuffer>
	{
		@Override
		public NetworkServer.ConnectingClientDescription<_ClientBuffer> userJoined(NetworkLayer.PeerToken token, String name)
		{
			return _createClient(token, name);
		}
		@Override
		public void userLeft(_ClientBuffer buffer)
		{
			_destroyClient(buffer);
		}
		@Override
		public void networkWriteReady(_ClientBuffer buffer)
		{
			_sendNextPacket(buffer);
		}
		@Override
		public void networkReadReady(_ClientBuffer buffer)
		{
			_setClientReadable(buffer);
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
		public Packet_MutationEntityFromClient peekOrRemoveNextMutationFromClient(int clientId, Packet_MutationEntityFromClient toRemove)
		{
			// The only packets which are currently sent after the handshake are entity mutations.
			return (Packet_MutationEntityFromClient) _peekOrRemoveNextMutationFromClient(clientId, toRemove);
			
		}
		@Override
		public void sendFullEntity(int clientId, Entity entity)
		{
			Packet_Entity packet = new Packet_Entity(entity);
			_bufferPacket(clientId, packet);
		}
		@Override
		public void sendPartialEntity(int clientId, PartialEntity entity)
		{
			Packet_PartialEntity packet = new Packet_PartialEntity(entity);
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
			_sendDisconnectRequest(clientId);
		}
	}


	private static class _ClientBuffer
	{
		public final NetworkLayer.PeerToken token;
		public final int clientId;
		public final Queue<Packet> outgoing;
		public boolean isNetworkWriteable;
		public final Queue<Packet> incoming;
		public boolean isNetworkReadable;
		
		public _ClientBuffer(NetworkLayer.PeerToken token, int clientId)
		{
			this.token = token;
			this.clientId = clientId;
			this.outgoing = new LinkedList<>();
			this.isNetworkWriteable = false;
			this.incoming = new LinkedList<>();
			this.isNetworkReadable = false;
		}
	}
}
