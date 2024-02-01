package com.jeffdisher.october.integration;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_MutationBlock;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_MutationEntityFromServer;
import com.jeffdisher.october.server.IServerAdapter;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.MessageQueue;


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
	/**
	 * Creates a shim with a started and connected ServerRunner.
	 * 
	 * @param millisPerTick The number of millis per tick in the server.
	 * @param currentTimeMillisProvider The provider of current time in millis.
	 * @return The shim.
	 * @throws InterruptedException The thread was interrupted while waiting for the server to start.
	 */
	public static LocalServerShim startedServerShim(long millisPerTick, LongSupplier currentTimeMillisProvider) throws InterruptedException
	{
		LocalServerShim shim = new LocalServerShim();
		shim._thread.start();
		ServerRunner server = new ServerRunner(millisPerTick, shim._serverAdapter, currentTimeMillisProvider);
		shim._waitForServer(server);
		return shim;
	}


	private static int CLIENT_ID = 1;

	private MessageQueue _queue = new MessageQueue();
	private Thread _thread = new Thread(() -> {
		_backgroundThreadMain();
	}, "LocalServerShim");
	private final ByteBuffer _packetBuffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES);
	private ClientAdapter _clientAdapter = new ClientAdapter();
	private ServerAdapter _serverAdapter = new ServerAdapter();
	private ServerRunner _server;
	private long _lastServerTick;

	private IServerAdapter.IListener _serverListener;
	private IClientAdapter.IListener _clientListener;

	private LocalServerShim()
	{
		// Private since we want to use the factory.
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
		synchronized (this)
		{
			while(null != _server)
			{
				this.wait();
			}
		}
		// We will also stop the shim here.
		_queue.shutdown();
		_thread.join();
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


	private void _backgroundThreadMain()
	{
		Runnable toRun = _queue.pollForNext(0, null);
		while (null != toRun)
		{
			toRun.run();
			toRun = _queue.pollForNext(0, null);
		}
	}

	private synchronized void _setConnectedClient(IClientAdapter.IListener clientListener)
	{
		Assert.assertTrue(null == _clientListener);
		_clientListener = clientListener;
		this.notifyAll();
	}

	private synchronized void _shutdownServer()
	{
		Assert.assertTrue(null != _server);
		_server.shutdown();
		_server = null;
		this.notifyAll();
	}

	private synchronized void _setServer(IServerAdapter.IListener serverListener)
	{
		Assert.assertTrue(null == _serverListener);
		_serverListener = serverListener;
		this.notifyAll();
	}

	private synchronized void _waitForServer(ServerRunner server) throws InterruptedException
	{
		Assert.assertTrue(null == _server);
		_server = server;
		while(null == _serverListener)
		{
			this.wait();
		}
	}

	private synchronized void _setLastServerTick(long tickNumber)
	{
		Assert.assertTrue((_lastServerTick + 1L) == tickNumber);
		_lastServerTick = tickNumber;
		this.notifyAll();
	}


	private class ClientAdapter implements IClientAdapter
	{
		@Override
		public void connectAndStartListening(IClientAdapter.IListener listener)
		{
			_queue.enqueue(() -> {
				_setConnectedClient(listener);
				_serverListener.clientConnected(CLIENT_ID);
				listener.adapterConnected(CLIENT_ID);
			});
		}
		@Override
		public void disconnect()
		{
			_queue.enqueue(() -> {
				_serverListener.clientDisconnected(CLIENT_ID);
				// There is only the one client so shut down the server.
				_shutdownServer();
			});
		}
		@Override
		public void sendChange(IMutationEntity change, long commitLevel)
		{
			_queue.enqueue(() -> {
				// Serialize and deserialize.
				Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
				Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change);
				PacketCodec.serializeToBuffer(_packetBuffer, packet);
				_packetBuffer.flip();
				Packet_MutationEntityFromClient decoded = (Packet_MutationEntityFromClient) PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
				Assert.assertTrue(!_packetBuffer.hasRemaining());
				_packetBuffer.clear();
				_serverListener.changeReceived(CLIENT_ID, decoded.mutation, commitLevel);
			});
		}
	}

	private class ServerAdapter implements IServerAdapter
	{
		@Override
		public void readyAndStartListening(IServerAdapter.IListener listener)
		{
			_queue.enqueue(() -> {
				_setServer(listener);
			});
		}
		@Override
		public void sendEntity(int clientId, Entity entity)
		{
			Assert.assertTrue(CLIENT_ID == clientId);
			_queue.enqueue(() -> {
				// Serialize and deserialize.
				Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
				Packet_Entity packet = new Packet_Entity(entity);
				PacketCodec.serializeToBuffer(_packetBuffer, packet);
				_packetBuffer.flip();
				Packet_Entity decoded = (Packet_Entity) PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
				Assert.assertTrue(!_packetBuffer.hasRemaining());
				_packetBuffer.clear();
				_clientListener.receivedEntity(decoded.entity);
			});
		}
		@Override
		public void sendCuboid(int clientId, IReadOnlyCuboidData cuboid)
		{
			Assert.assertTrue(CLIENT_ID == clientId);
			_queue.enqueue(() -> {
				// Note that the size of a single packet is 64 KiB but a cuboid can have multiple aspects, some of which can be very large.  Therefore, we need to carve this into many packets.
				Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
				CuboidCodec.Serializer serializer = new CuboidCodec.Serializer(cuboid);
				Packet packetToSend = serializer.getNextPacket();
				CuboidCodec.Deserializer deserializer = null;
				CuboidData result = null;
				while (null != packetToSend)
				{
					PacketCodec.serializeToBuffer(_packetBuffer, packetToSend);
					_packetBuffer.flip();
					Packet decoded = PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
					Assert.assertTrue(!_packetBuffer.hasRemaining());
					_packetBuffer.clear();
					
					// Handle this - we know that the first must be the start while the others are fragments.
					if (null == deserializer)
					{
						deserializer = new CuboidCodec.Deserializer((Packet_CuboidStart) decoded);
					}
					else
					{
						result = deserializer.processPacket((Packet_CuboidFragment) decoded);
					}
					packetToSend = serializer.getNextPacket();
					// Once we see no more work, we must be done.
					Assert.assertTrue((null == packetToSend) == (null != result));
				}
				_clientListener.receivedCuboid(result);
			});
		}
		@Override
		public void sendChange(int clientId, int entityId, IMutationEntity change)
		{
			Assert.assertTrue(CLIENT_ID == clientId);
			_queue.enqueue(() -> {
				// Serialize and deserialize.
				Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
				Packet_MutationEntityFromServer packet = new Packet_MutationEntityFromServer(entityId, change);
				PacketCodec.serializeToBuffer(_packetBuffer, packet);
				_packetBuffer.flip();
				Packet_MutationEntityFromServer decoded = (Packet_MutationEntityFromServer) PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
				Assert.assertTrue(!_packetBuffer.hasRemaining());
				_packetBuffer.clear();
				_clientListener.receivedChange(decoded.entityId, decoded.mutation);
			});
		}
		@Override
		public void sendMutation(int clientId, IMutationBlock mutation)
		{
			Assert.assertTrue(CLIENT_ID == clientId);
			_queue.enqueue(() -> {
				// Serialize and deserialize.
				Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
				Packet_MutationBlock packet = new Packet_MutationBlock(mutation);
				PacketCodec.serializeToBuffer(_packetBuffer, packet);
				_packetBuffer.flip();
				Packet_MutationBlock decoded = (Packet_MutationBlock) PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
				Assert.assertTrue(!_packetBuffer.hasRemaining());
				_packetBuffer.clear();
				_clientListener.receivedMutation(decoded.mutation);
			});
		}
		@Override
		public void sendEndOfTick(int clientId, long tickNumber, long latestLocalCommitIncluded)
		{
			_queue.enqueue(() -> {
				if (ServerRunner.FAKE_CLIENT_ID == clientId)
				{
					_setLastServerTick(tickNumber);
				}
				else
				{
					Assert.assertTrue(CLIENT_ID == clientId);
					Assert.assertTrue(PacketCodec.MAX_PACKET_BYTES == _packetBuffer.remaining());
					Packet_EndOfTick packet = new Packet_EndOfTick(tickNumber, latestLocalCommitIncluded);
					PacketCodec.serializeToBuffer(_packetBuffer, packet);
					_packetBuffer.flip();
					Packet_EndOfTick decoded = (Packet_EndOfTick) PacketCodec.parseAndSeekFlippedBuffer(_packetBuffer);
					Assert.assertTrue(!_packetBuffer.hasRemaining());
					_packetBuffer.clear();
					_clientListener.receivedEndOfTick(decoded.tickNumber, decoded.latestLocalCommitIncluded);
				}
			});
		}
		@Override
		public void disconnectClient(int clientId)
		{
			Assert.assertTrue(CLIENT_ID == clientId);
			_queue.enqueue(() -> {
				_clientListener.adapterDisconnected();
			});
		}
	}
}
