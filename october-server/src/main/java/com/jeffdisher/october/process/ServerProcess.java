package com.jeffdisher.october.process;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

import com.jeffdisher.october.net.NetworkLayer;
import com.jeffdisher.october.net.NetworkServer;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.PacketFromServer;
import com.jeffdisher.october.net.Packet_ServerSendConfigUpdate;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.OutpacketBuffer;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Meant to be a more-or-less self-contained server process.  It internally runs NetworkServer and ServerRunner.
 */
public class ServerProcess
{
	private final WorldConfig _sharedConfigInstance;
	private final Map<Integer, ClientBuffer> _clientsById;
	private final Set<Integer> _partialDisconnectIds;
	private final ServerRunner _server;
	private final NetworkServer<ClientBuffer> _network;

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
	 * @param monitoringAgent Stores descriptive information about the state of the server, for external monitoring.
	 * @param config The configuration of this world.
	 * @throws IOException There was an error starting up the network.
	 */
	public ServerProcess(int port
			, long millisPerTick
			, ResourceLoader cuboidLoader
			, LongSupplier currentTimeMillisProvider
			, MonitoringAgent monitoringAgent
			, WorldConfig config
	) throws IOException
	{
		_sharedConfigInstance = config;
		_clientsById = new HashMap<>();
		_partialDisconnectIds = new HashSet<>();
		// We will assume that ServerRunner will shut down the cuboidLoader for us.
		_server = new ServerRunner(millisPerTick
				, new _ServerListener()
				, cuboidLoader
				, currentTimeMillisProvider
				, monitoringAgent
				, config
		);
		// The server passes its listener back within the constructor so we should see that, now.
		Assert.assertTrue(null != _serverListener);
		_network = new NetworkServer<ClientBuffer>(new _NetworkListener()
				, currentTimeMillisProvider
				, port
				, millisPerTick
				, config.clientViewDistanceMaximum
		);
		monitoringAgent.setNetwork(_network);
	}

	/**
	 * Specifically called by tests to wait for a certain number of ticks to advance.
	 * Blocks until at least the given ticksToAdvance have completed.
	 * 
	 * @param ticksToAdvance The minimum number of takes to wait before returning.
	 * @return The last tick number which completed.
	 * @throws InterruptedException If the waiting thread was interrupted.
	 */
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

	private synchronized NetworkServer.ConnectingClientDescription<ClientBuffer> _createClient(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
	{
		// For now, we will just hash the string and use that as the ID (Java's string hash is reasonable).  We still want the number to be positive, though.
		// In the future, we probably want an in-memory whitelist so this can remain synchronous.
		NetworkServer.ConnectingClientDescription<ClientBuffer> result;
		int hash = Math.abs(name.hashCode());
		if (0 == hash)
		{
			// This is a corner-case but we never want a non-negative value so we work around 0 here.
			hash = 1;
		}
		if (_clientsById.containsKey(hash) || _partialDisconnectIds.contains(hash))
		{
			// Already present, so fail.
			result = null;
		}
		else
		{
			// This is valid so install it.
			ClientBuffer buffer = new ClientBuffer(token, hash);
			_clientsById.put(hash, buffer);
			_serverListener.clientConnected(hash, token, name, cuboidViewDistance);
			result = new NetworkServer.ConnectingClientDescription<>(hash, buffer);
			
			// In a last step, before we return, we want to pre-seed the ClientBuffer with configuration data.
			Packet_ServerSendConfigUpdate configPacket = new Packet_ServerSendConfigUpdate(_sharedConfigInstance.ticksPerDay
					, _sharedConfigInstance.dayStartTick
			);
			ByteBuffer mustNotBeReady = buffer.shouldImmediatelySendBuffer(configPacket);
			// We are interjecting before the state machine starts changing so we assume that this isn't ready to send.
			Assert.assertTrue(null == mustNotBeReady);
		}
		return result;
	}

	private synchronized void _destroyClient(ClientBuffer buffer)
	{
		// We will put this in the partial disconnects so that the higher-level needs to ack it.
		// (this avoids racy reconnects seeing old data before the high-level decides to send new data)
		_partialDisconnectIds.add(buffer.clientId);
		
		_serverListener.clientDisconnected(buffer.clientId);
		_clientsById.remove(buffer.clientId);
	}

	private synchronized void _sendNextPacket(ClientBuffer buffer, ByteBuffer bufferToWrite)
	{
		ByteBuffer toWrite = buffer.writeImmediateForWriteableClient(bufferToWrite);
		if (null != toWrite)
		{
			_network.sendBuffer(buffer.token, toWrite);
		}
	}

	private synchronized void _bufferPacket(int clientId, PacketFromServer packet)
	{
		ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			Assert.assertTrue(_partialDisconnectIds.contains(clientId));
			System.out.println("Warning: Ignoring buffer packet to client " + clientId);
		}
		else
		{
			// This will take us out of the writeable state if it returns non-null.
			ByteBuffer toWrite = buffer.shouldImmediatelySendBuffer(packet);
			if (null != toWrite)
			{
				_network.sendBuffer(buffer.token, toWrite);
			}
		}
	}

	private synchronized OutpacketBuffer _openOutputBuffer(int clientId)
	{
		OutpacketBuffer result;
		ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			Assert.assertTrue(_partialDisconnectIds.contains(clientId));
			// We will return a buffer, to keep the caller simple (since this is a rare case) and ignore its close.
			System.out.println("Warning: Creating OutpacketBuffer for disconnected client " + clientId);
			// (this -1 for the size will allow us to detect and drop this, later - in case the client reconnects).
			result = new OutpacketBuffer(null, -1);
		}
		else
		{
			// This will take us out of the writeable state.
			result = buffer.openOutpacketBuffer();
		}
		return result;
	}

	private synchronized void _closeOutputBuffer(int clientId, OutpacketBuffer closing)
	{
		if (-1 == closing.clientListSize)
		{
			// This was opened for a disconnected client so ignore it.
		}
		else
		{
			ClientBuffer buffer = _clientsById.get(clientId);
			if (null == buffer)
			{
				// This is due to a race:  Disconnects come from the network while packets come from the logic.
				Assert.assertTrue(_partialDisconnectIds.contains(clientId));
				// We will return a buffer, to keep the caller simple (since this is a rare case) and ignore its close.
				System.out.println("Warning: Dropping OutpacketBuffer for disconnected client " + clientId);
			}
			else
			{
				ByteBuffer toWrite = buffer.shouldImmediatelySendAfterClosingOutpacket(closing);
				if (null != toWrite)
				{
					_network.sendBuffer(buffer.token, toWrite);
				}
			}
		}
	}

	private synchronized void _sendDisconnectRequest(int clientId)
	{
		ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			Assert.assertTrue(_partialDisconnectIds.contains(clientId));
			System.out.println("Warning: Ignoring disconnect request to client " + clientId);
		}
		else
		{
			_network.disconnectClient(buffer.token);
		}
	}

	private synchronized void _ackDisconnect(int clientId)
	{
		boolean didAck = _partialDisconnectIds.remove(clientId);
		Assert.assertTrue(didAck);
	}

	public synchronized void _updateTickNumber(long tickNumber)
	{
		_latestTickNumber = tickNumber;
		this.notifyAll();
	}

	private synchronized void _setClientReadable(ClientBuffer buffer)
	{
		boolean shouldNotify = buffer.becameReadableAfterNetworkReady();
		if (shouldNotify)
		{
			_serverListener.clientReadReady(buffer.clientId);
		}
	}

	private synchronized PacketFromClient _peekOrRemoveNextMutationFromClient(int clientId, PacketFromClient toRemove)
	{
		PacketFromClient packet;
		ClientBuffer buffer = _clientsById.get(clientId);
		if (null == buffer)
		{
			// This is due to a race:  Disconnects come from the network while packets come from the logic.
			Assert.assertTrue(_partialDisconnectIds.contains(clientId));
			System.out.println("Warning: Ignoring peek next mutation from client " + clientId);
			packet = null;
		}
		else
		{
			packet = buffer.peekOrRemoveNextPacket(toRemove, () -> _network.readBufferedPackets(buffer.token));
		}
		return packet;
	}

	private synchronized NetworkServer.ServerStatus _pollServerStatus()
	{
		String serverName = _sharedConfigInstance.serverName;
		int clientCount = _clientsById.size();
		return new NetworkServer.ServerStatus(serverName, clientCount);
	}


	// Note that all callbacks are issued on the NetworkServer's thread.
	private class _NetworkListener implements NetworkServer.IListener<ClientBuffer>
	{
		@Override
		public NetworkServer.ConnectingClientDescription<ClientBuffer> userJoined(NetworkLayer.PeerToken token, String name, int cuboidViewDistance)
		{
			return _createClient(token, name, cuboidViewDistance);
		}
		@Override
		public void userLeft(ClientBuffer buffer)
		{
			_destroyClient(buffer);
		}
		@Override
		public void networkWriteReady(ClientBuffer buffer, ByteBuffer bufferToWrite)
		{
			_sendNextPacket(buffer, bufferToWrite);
		}
		@Override
		public void networkReadReady(ClientBuffer buffer)
		{
			_setClientReadable(buffer);
		}
		@Override
		public NetworkServer.ServerStatus pollServerStatus()
		{
			return _pollServerStatus();
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
		public PacketFromClient peekOrRemoveNextPacketFromClient(int clientId, PacketFromClient toRemove)
		{
			return _peekOrRemoveNextMutationFromClient(clientId, toRemove);
		}
		@Override
		public void sendPacket(int clientId, PacketFromServer packet)
		{
			_bufferPacket(clientId, packet);
		}
		@Override
		public OutpacketBuffer openOutputBuffer(int clientId)
		{
			return _openOutputBuffer(clientId);
		}
		@Override
		public void closeOutputBuffer(int clientId, OutpacketBuffer buffer)
		{
			_closeOutputBuffer(clientId, buffer);
		}
		@Override
		public void testingEndOfTick(long tickNumber)
		{
			_updateTickNumber(tickNumber);
		}
		@Override
		public void disconnectClient(int clientId)
		{
			// We just pass this along since we will handle any state update when we see the disconnected callback (so there is only one relevant path).
			_sendDisconnectRequest(clientId);
		}
		@Override
		public void acknowledgeDisconnect(int clientId)
		{
			// This comes in from the higher-level AFTER we have told them that the network disconnected asynchronously so that the higher-level acknowledges this.
			_ackDisconnect(clientId);
		}
	}
}
