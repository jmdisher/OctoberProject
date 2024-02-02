package com.jeffdisher.october.integration;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.LongSupplier;

import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_MutationBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromServer;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.utils.Assert;


/**
 * Intended to be used by clients which support purely single-player modes to connect client-side logic to an embedded
 * server process.
 * All data passed through this mechanism is serialized and deserialized in order to verify that instance assumptions
 * and codec details are fully exercised in both single-player and multi-player modes, reducing the testing surface
 * area.
 * A thread running within the shim ferries this data between the client and server to add the expected sort of
 * asynchronous timing.
 */
public class LocalServerShim
{
	public static final int PORT = 5678;

	/**
	 * Creates a shim with a started and connected ServerRunner.
	 * 
	 * @param millisPerTick The number of millis per tick in the server.
	 * @param currentTimeMillisProvider The provider of current time in millis.
	 * @return The shim.
	 * @throws IOException Failure starting the network.
	 */
	public static LocalServerShim startedServerShim(long millisPerTick, LongSupplier currentTimeMillisProvider) throws IOException
	{
		// Create the self-contained server process.
		ServerProcess server = new ServerProcess(PORT, millisPerTick, currentTimeMillisProvider);
		return new LocalServerShim(server);
	}


	private final ServerProcess _server;
	private final LowLevelNetwork _network;
	private NetworkClient _client;
	private final ClientAdapter _clientAdapter;
	private long _lastServerTick;

	private IClientAdapter.IListener _clientListener;

	private LocalServerShim(ServerProcess server)
	{
		// Private since we want to use the factory.
		_server = server;
		_network = new LowLevelNetwork();
		_clientAdapter = new ClientAdapter();
	}

	/**
	 * Provides access to the client adapter for the fake network to use in creating a ClientRunner.
	 * 
	 * @return The client-side of the network connected to this server.
	 */
	public IClientAdapter getClientAdapter()
	{
		return _clientAdapter;
	}

	/**
	 * Blocks until a client connects.  Is expected to be called after creating a ServerRunner attached to the client
	 * adapter.
	 * 
	 * @throws InterruptedException Interrupted while waiting for the client to appear.
	 */
	public synchronized void waitForClient() throws InterruptedException
	{
		while(null == _clientListener)
		{
			this.wait();
		}
	}

	/**
	 * Blocks until the given number of ticks have elapsed since the call was issued.
	 * 
	 * @param tickCount The number of ticks to delay.
	 * @throws InterruptedException Interrupted while waiting for the ticks to complete.
	 */
	public synchronized void waitForTickAdvance(long tickCount) throws InterruptedException
	{
		// Make sure that we have seen a tick, first (since we may not have seen something yet).
		while(0 == _lastServerTick)
		{
			this.wait();
		}
		// Now wait for this many ticks to advance.
		long tickNumber = _lastServerTick + tickCount;
		while(_lastServerTick < tickNumber)
		{
			this.wait();
		}
	}

	/**
	 * Blocks until the server has shut down.  Note that this doesn't request the shutdown, but the client's disconnect
	 * will request the server shutdown.
	 * 
	 * @throws InterruptedException Interrupted while waiting for the server to shutdown.
	 */
	public void waitForServerShutdown() throws InterruptedException
	{
		_server.stop();
	}

	/**
	 * A temporary helper to inject a cuboid directly into the server until we can rely on a cuboid generator.
	 * 
	 * @param cuboid The cuboid.
	 */
	public void injectCuboidToServer(CuboidData cuboid)
	{
		// TODO:  Remove this once cuboids are loaded via a generator injected into the server.
		_server.loadCuboid(cuboid);
	}


	private synchronized void _setConnectedClient(IClientAdapter.IListener clientListener)
	{
		Assert.assertTrue(null == _clientListener);
		_clientListener = clientListener;
		try
		{
			_client = new NetworkClient(_network, InetAddress.getLocalHost(), PORT, "Client");
		}
		catch (IOException e)
		{
			// TODO:  Determine how we want to handle this.
			throw Assert.unexpected(e);
		}
		this.notifyAll();
	}

	private synchronized void _setLastServerTick(long tickNumber)
	{
		if (0 != _lastServerTick)
		{
			Assert.assertTrue((_lastServerTick + 1L) == tickNumber);
		}
		_lastServerTick = tickNumber;
		this.notifyAll();
	}


	private class ClientAdapter implements IClientAdapter
	{
		@Override
		public void connectAndStartListening(IClientAdapter.IListener listener)
		{
			_setConnectedClient(listener);
		}
		@Override
		public void disconnect()
		{
			_client.stop();
		}
		@Override
		public void sendChange(IMutationEntity change, long commitLevel)
		{
			Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change, commitLevel);
			_network.bufferPacket(packet);
		}
	}


	private class LowLevelNetwork implements NetworkClient.IListener
	{
		// We just keep the outgoing buffer state here, for convenience.
		private boolean _networkReady = false;
		private final Queue<Packet> _outgoing = new LinkedList<>();
		
		private CuboidCodec.Deserializer _deserializer = null;
		
		public synchronized void bufferPacket(Packet packet)
		{
			if (_networkReady)
			{
				_client.sendMessage(packet);
				_networkReady = false;
			}
			else
			{
				_outgoing.add(packet);
			}
		}
		@Override
		public synchronized void handshakeCompleted(int assignedId)
		{
			_clientListener.adapterConnected(assignedId);
		}
		@Override
		public synchronized void networkReady()
		{
			Assert.assertTrue(!_networkReady);
			if (_outgoing.isEmpty())
			{
				_networkReady = true;
			}
			else
			{
				Packet packet = _outgoing.poll();
				_client.sendMessage(packet);
			}
		}
		@Override
		public void packetReceived(Packet packet)
		{
			// We need to decode this for the ClientRunner.
			// For now, we will just do this with type checks although we might want to use a virtual call into the Packet to clean this up, in the future.
			if (packet instanceof Packet_CuboidStart)
			{
				Assert.assertTrue(null == _deserializer);
				_deserializer = new CuboidCodec.Deserializer((Packet_CuboidStart) packet);
			}
			else if (packet instanceof Packet_CuboidFragment)
			{
				CuboidData done = _deserializer.processPacket((Packet_CuboidFragment) packet);
				if (null != done)
				{
					_deserializer = null;
					_clientListener.receivedCuboid(done);
				}
			}
			else if (packet instanceof Packet_Entity)
			{
				_clientListener.receivedEntity(((Packet_Entity)packet).entity);
			}
			else if (packet instanceof Packet_MutationEntityFromClient)
			{
				// The client never receives this type.
				throw Assert.unreachable();
			}
			else if (packet instanceof Packet_MutationEntityFromServer)
			{
				Packet_MutationEntityFromServer safe = (Packet_MutationEntityFromServer) packet;
				_clientListener.receivedChange(safe.entityId, safe.mutation);
			}
			else if (packet instanceof Packet_MutationBlock)
			{
				Packet_MutationBlock safe = (Packet_MutationBlock) packet;
				_clientListener.receivedMutation(safe.mutation);
			}
			else if (packet instanceof Packet_EndOfTick)
			{
				Packet_EndOfTick safe = (Packet_EndOfTick) packet;
				_setLastServerTick(safe.tickNumber);
				_clientListener.receivedEndOfTick(safe.tickNumber, safe.latestLocalCommitIncluded);
			}
		}
	}
}
