package com.jeffdisher.october.process;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.client.ClientRunner;
import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.client.SpeculativeProjection;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidCodec;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.net.NetworkClient;
import com.jeffdisher.october.net.Packet;
import com.jeffdisher.october.net.PacketFromClient;
import com.jeffdisher.october.net.Packet_CuboidFragment;
import com.jeffdisher.october.net.Packet_CuboidStart;
import com.jeffdisher.october.net.Packet_EndOfTick;
import com.jeffdisher.october.net.Packet_Entity;
import com.jeffdisher.october.net.Packet_BlockStateUpdate;
import com.jeffdisher.october.net.Packet_ClientJoined;
import com.jeffdisher.october.net.Packet_ClientLeft;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_PartialEntity;
import com.jeffdisher.october.net.Packet_PartialEntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_ReceiveChatMessage;
import com.jeffdisher.october.net.Packet_EntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_RemoveCuboid;
import com.jeffdisher.october.net.Packet_RemoveEntity;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.net.Packet_ServerSendConfigUpdate;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Internally manages connecting to the server, making updates to an internal projection, translating calls into
 * mutations, sending the mutations to the server, and maintaining network state.
 * Exposes a high-level action interface and callbacks (called on caller thread when it is available) related to changes
 * to the game state.  Internally, this is essentially just connecting ClientRunner to NetworkClient.
 */
public class ClientProcess
{
	private final IListener _listener;
	private final _LockingList _pendingCallbacks;
	private final ClientRunner _clientRunner;
	private IClientAdapter.IListener _messagesToClientRunner;

	// State which can be used in synchronization, as it is updated by background threads.
	private long _lastTickFromServer;
	private int _assignedClientId;
	private boolean _isEntityLoaded;

	private final NetworkClient _client;

	// Network buffer state.
	private final ReentrantLock _networkBufferLock;
	private boolean _networkReady;
	private final Queue<PacketFromClient> _outgoing;

	/**
	 * Creates and starts up a new client process.  Returns once the connection is established, but before the handshake
	 * is complete.
	 * 
	 * @param listener The callback interface which will receive updates.
	 * @param address The address of the server.
	 * @param port The port number of the server.
	 * @param clientName The name the client should use to identify itself.
	 * @throws IOException There was a network error connecting to the server.
	 */
	public ClientProcess(IListener listener, InetAddress address, int port, String clientName) throws IOException
	{
		_listener = listener;
		_pendingCallbacks = new _LockingList();
		_clientRunner = new ClientRunner(new _NetworkAdapter(), new _ProjectionListener(), new _RunnerListener());
		// This should be set once the runner is started but before it returns.
		Assert.assertTrue(null != _messagesToClientRunner);
		
		// Create the connection (note that this will return when the connection is accepted but the handshake completes in the background).
		_client = new NetworkClient(new _NetworkClientListener(), address, port, clientName);
		
		_networkBufferLock = new ReentrantLock();
		_networkReady = false;
		_outgoing = new LinkedList<>();
	}

	/**
	 * Shuts down the local client logic and network connection.
	 */
	public void disconnect()
	{
		_clientRunner.disconnect();
	}

	/**
	 * The generic way to send actions into the client layer.  Changes will be applied to the local projection and sent
	 * to the server.
	 * 
	 * @param change The change to apply.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void sendAction(IMutationEntity<IMutablePlayerEntity> change, long currentTimeMillis)
	{
		_clientRunner.commonApplyEntityAction(change, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Waits until this client's ID has been received with the end of the handshake.
	 * 
	 * @return The client's ID.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized int waitForClientId() throws InterruptedException
	{
		while (0 == _assignedClientId)
		{
			this.wait();
		}
		return _assignedClientId;
	}

	/**
	 * Waits until this client's first entity packet has arrived and returns the latest tick observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized long waitForLocalEntity(long currentTimeMillis) throws InterruptedException
	{
		// We want to wait until we see the end of at least a single tick since we don't want to return after the first
		// entity packet, but before the first tick packet (would give us 0).
		while (0 == _lastTickFromServer)
		{
			this.wait();
		}
		// Now, we can wait for the entity (although it likely came in before the tick).
		while (!_isEntityLoaded)
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		return _lastTickFromServer;
	}

	/**
	 * Waits until the packet with the given tick number has been observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 */
	public synchronized long waitForTick(long tickNumber, long currentTimeMillis) throws InterruptedException
	{
		while (_lastTickFromServer < tickNumber)
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		return _lastTickFromServer;
	}

	/**
	 * Runs any pending call-outs.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void runPendingCalls(long currentTimeMillis)
	{
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Sends a single break block change, targeting the given block location.  Note that it typically takes many such
	 * changes to break a block.
	 * 
	 * @param blockLocation The location of the block to break.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void hitBlock(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		_clientRunner.hitBlock(blockLocation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Sends a single repair block change, targeting the given block location.  Note that it may take many such
	 * changes to fully repair a block.
	 * 
	 * @param blockLocation The location of the block to repair.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void repairBlock(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		_clientRunner.repairBlock(blockLocation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection along the given
	 * horizontal direction for the amount of time which has passed since the last call.
	 * This will also apply z-acceleration for that amount of time and will handle cases such as collision but will at
	 * least attempt to move in this way (and will send the change to the server).
	 * 
	 * @param direction The direction to move.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void moveHorizontalFully(EntityChangeMove.Direction direction, long currentTimeMillis)
	{
		_clientRunner.moveHorizontalFully(direction, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection in the given
	 * direction, relative to the current orientation, for the amount of time which has passed since the last call.
	 * This will also apply z-acceleration for that amount of time and will handle cases such as collision but will at
	 * least attempt to move in this way (and will send the change to the server).
	 * 
	 * @param relativeDirection The direction to move, relative to the current orientation.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void accelerateHorizontally(EntityChangeAccelerate.Relative relativeDirection, long currentTimeMillis)
	{
		_clientRunner.accelerateHorizontally(relativeDirection, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Requests a crafting operation start.
	 * 
	 * @param operation The crafting operation to run.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void craft(Craft operation, long currentTimeMillis)
	{
		_clientRunner.craft(operation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Requests that we start or continue a crafting operation in a block.  Note that these calls must be made
	 * explicitly and cannot be implicitly continued in "doNothing" the way some other crafting operations can be, since
	 * we don't know what the user is looking at.
	 * 
	 * @param block The crafting station location.
	 * @param operation The crafting operation to run (can be null if just continuing what is already happening).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void craftInBlock(AbsoluteLocation block, Craft operation, long currentTimeMillis)
	{
		_clientRunner.craftInBlock(block, operation, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Allows time to pass to account for things like falling, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		_clientRunner.doNothing(currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Sends a chat message to targetId.
	 * 
	 * @param targetId The ID of the target client (0 to mean "all").
	 * @param message The message to send.
	 */
	public void sentChatMessage(int targetId, String message)
	{
		_clientRunner.sentChatMessage(targetId, message);
	}

	/**
	 * Updates the internal last action times and runs callbacks without applying changes to the projection or network.
	 * This is primarily used in the case where the associated server is paused and the client must skip over that time
	 * without sending its usual periodic updates.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void advanceTime(long currentTimeMillis)
	{
		_clientRunner.advanceTime(currentTimeMillis);
		_runPendingCallbacks();
	}


	private void _background_bufferPacket(PacketFromClient packet)
	{
		_networkBufferLock.lock();
		try
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
		finally
		{
			_networkBufferLock.unlock();
		}
	}

	private void _background_networkReadyForWrite()
	{
		_networkBufferLock.lock();
		try
		{
			Assert.assertTrue(!_networkReady);
			if (_outgoing.isEmpty())
			{
				_networkReady = true;
			}
			else
			{
				PacketFromClient packet = _outgoing.poll();
				_client.sendMessage(packet);
			}
		}
		finally
		{
			_networkBufferLock.unlock();
		}
	}

	private synchronized void _background_updateTickNumber(long latestTickNumer)
	{
		Assert.assertTrue((0 == _lastTickFromServer) || ((_lastTickFromServer + 1) == latestTickNumer));
		_lastTickFromServer = latestTickNumer;
		this.notifyAll();
	}

	private synchronized void _background_setClientId(int assignedId)
	{
		Assert.assertTrue(0 == _assignedClientId);
		Assert.assertTrue(assignedId > 0);
		_assignedClientId = assignedId;
		this.notifyAll();
	}

	private synchronized void _background_entityLoaded(int entityId)
	{
		if (!_isEntityLoaded && (entityId == _assignedClientId))
		{
			_isEntityLoaded = true;
			this.notifyAll();
		}
	}

	private void _runPendingCallbacks()
	{
		Runnable next = _pendingCallbacks.getNext();
		while (null != next)
		{
			next.run();
			next = _pendingCallbacks.getNext();
		}
	}


	// NOTE:  These callbacks are issued on the background network thread.
	private class _NetworkClientListener implements NetworkClient.IListener
	{
		private CuboidCodec.Deserializer _deserializer = null;
		
		@Override
		public void handshakeCompleted(int assignedId, long millisPerTick)
		{
			_messagesToClientRunner.adapterConnected(assignedId, millisPerTick);
			_background_setClientId(assignedId);
		}
		@Override
		public synchronized void networkReady()
		{
			_background_networkReadyForWrite();
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
					_messagesToClientRunner.receivedCuboid(done);
				}
			}
			else if (packet instanceof Packet_Entity)
			{
				Packet_Entity safe = (Packet_Entity)packet;
				_messagesToClientRunner.receivedFullEntity(safe.entity);
				_background_entityLoaded(safe.entity.id());
			}
			else if (packet instanceof Packet_PartialEntity)
			{
				Packet_PartialEntity safe = (Packet_PartialEntity)packet;
				_messagesToClientRunner.receivedPartialEntity(safe.entity);
				_background_entityLoaded(safe.entity.id());
			}
			else if (packet instanceof Packet_MutationEntityFromClient)
			{
				// The client never receives this type.
				throw Assert.unreachable();
			}
			else if (packet instanceof Packet_EntityUpdateFromServer)
			{
				Packet_EntityUpdateFromServer safe = (Packet_EntityUpdateFromServer) packet;
				_messagesToClientRunner.receivedEntityUpdate(safe.entityId, safe.update);
			}
			else if (packet instanceof Packet_PartialEntityUpdateFromServer)
			{
				Packet_PartialEntityUpdateFromServer safe = (Packet_PartialEntityUpdateFromServer) packet;
				_messagesToClientRunner.receivedPartialEntityUpdate(safe.entityId, safe.update);
			}
			else if (packet instanceof Packet_BlockStateUpdate)
			{
				Packet_BlockStateUpdate safe = (Packet_BlockStateUpdate) packet;
				_messagesToClientRunner.receivedBlockUpdate(safe.stateUpdate);
			}
			else if (packet instanceof Packet_EndOfTick)
			{
				Packet_EndOfTick safe = (Packet_EndOfTick) packet;
				_messagesToClientRunner.receivedEndOfTick(safe.tickNumber, safe.latestLocalCommitIncluded);
				_background_updateTickNumber(safe.tickNumber);
			}
			else if (packet instanceof Packet_RemoveEntity)
			{
				Packet_RemoveEntity safe = (Packet_RemoveEntity)packet;
				_messagesToClientRunner.removeEntity(safe.entityId);
			}
			else if (packet instanceof Packet_RemoveCuboid)
			{
				Packet_RemoveCuboid safe = (Packet_RemoveCuboid)packet;
				_messagesToClientRunner.removeCuboid(safe.address);
			}
			else if (packet instanceof Packet_ServerSendConfigUpdate)
			{
				Packet_ServerSendConfigUpdate safe = (Packet_ServerSendConfigUpdate)packet;
				_messagesToClientRunner.receivedConfigUpdate(safe.ticksPerDay, safe.dayStartTick);
			}
			else if (packet instanceof Packet_ClientJoined)
			{
				Packet_ClientJoined safe = (Packet_ClientJoined)packet;
				_messagesToClientRunner.receivedOtherClientJoined(safe.clientId, safe.clientName);
			}
			else if (packet instanceof Packet_ClientLeft)
			{
				Packet_ClientLeft safe = (Packet_ClientLeft)packet;
				_messagesToClientRunner.receivedOtherClientLeft(safe.clientId);
			}
			else if (packet instanceof Packet_ReceiveChatMessage)
			{
				Packet_ReceiveChatMessage safe = (Packet_ReceiveChatMessage)packet;
				_messagesToClientRunner.receivedChatMessage(safe.sourceId, safe.message);
			}
			else
			{
				// If this is something unknown, there is a missing handler here.
				throw Assert.unreachable();
			}
		}
		@Override
		public void serverDisconnected()
		{
			_messagesToClientRunner.adapterDisconnected();
		}
	}

	// NOTE:  These callbacks are issued on the background network thread.
	private class _NetworkAdapter implements IClientAdapter
	{
		@Override
		public void connectAndStartListening(IListener listener)
		{
			Assert.assertTrue(null == _messagesToClientRunner);
			_messagesToClientRunner = listener;
		}
		@Override
		public void disconnect()
		{
			_client.stop();
		}
		@Override
		public void sendChange(IMutationEntity<IMutablePlayerEntity> change, long commitLevel)
		{
			Packet_MutationEntityFromClient packet = new Packet_MutationEntityFromClient(change, commitLevel);
			_background_bufferPacket(packet);
		}
		@Override
		public void sendChatMessage(int targetClientId, String message)
		{
			Assert.assertTrue(targetClientId >= 0);
			Assert.assertTrue(null != message);
			Packet_SendChatMessage packet = new Packet_SendChatMessage(targetClientId, message);
			_background_bufferPacket(packet);
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _ProjectionListener implements SpeculativeProjection.IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidLoad(cuboid, heightMap);
			});
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidChange(cuboid, heightMap, changedBlocks);
			});
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidUnload(address);
			});
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			_pendingCallbacks.add(() -> {
				_listener.thisEntityDidLoad(authoritativeEntity);
			});
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			_pendingCallbacks.add(() -> {
				_listener.thisEntityDidChange(authoritativeEntity, projectedEntity);
			});
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.otherEntityDidLoad(entity);
			});
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.otherEntityDidChange(entity);
			});
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			_pendingCallbacks.add(() -> {
				_listener.otherEntityDidUnload(id);
			});
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			_pendingCallbacks.add(() -> {
				_listener.tickDidComplete(gameTick);
			});
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _RunnerListener implements ClientRunner.IListener
	{
		@Override
		public void clientDidConnectAndLogin(int assignedLocalEntityId)
		{
			_pendingCallbacks.add(() -> {
				_listener.connectionEstablished(assignedLocalEntityId);
			});
		}
		@Override
		public void clientDisconnected()
		{
			_pendingCallbacks.add(() -> {
				_listener.connectionClosed();
			});
		}
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
			_pendingCallbacks.add(() -> {
				_listener.configUpdated(ticksPerDay, dayStartTick);
			});
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
			_pendingCallbacks.add(() -> {
				_listener.otherClientJoined(clientId, name);
			});
		}
		@Override
		public void otherClientLeft(int clientId)
		{
			_pendingCallbacks.add(() -> {
				_listener.otherClientLeft(clientId);
			});
		}
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			_pendingCallbacks.add(() -> {
				_listener.receivedChatMessage(senderId, message);
			});
		}
	}

	private static class _LockingList
	{
		private final Queue<Runnable> _queue = new LinkedList<>();
		public synchronized void add(Runnable next)
		{
			_queue.add(next);
		}
		public synchronized Runnable getNext()
		{
			return _queue.isEmpty()
					? null
					: _queue.poll()
			;
		}
	}

	/**
	 * Note that these callbacks are issued on the caller's thread when it calls into the process, but they are buffered
	 * between those calls.  This means that minimally something like runPendingCalls() must be called, periodically.
	 */
	public interface IListener
	{
		/**
		 * Called when the connection first comes online and the ID of the client's entity is discovered.
		 * Only called once per instance.
		 * 
		 * @param assignedEntityId The ID of this client's entity (>0).
		 */
		void connectionEstablished(int assignedEntityId);
		/**
		 * Called when the server connection is closed.  The ClientProcess will have to be discarded and recreated to
		 * re-establish the connection.
		 * Only called once per instance.
		 */
		void connectionClosed();
		
		/**
		 * Called when a new cuboid is loaded (may have been previously unloaded but not currently loaded).
		 * 
		 * @param cuboid The read-only cuboid data.
		 * @param heightMap The height map for this cuboid's column.
		 */
		void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap);
		/**
		 * Called when a new cuboid is replaced due to changes (must have been previously loaded).
		 * 
		 * @param cuboid The read-only cuboid data.
		 * @param heightMap The height map for this cuboid's column.
		 * @param changedBlocks The set of blocks which have some kind of change.
		 */
		void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks);
		/**
		 * Called when a new cuboid should be unloaded as the server is no longer telling the client about it.
		 * 
		 * @param address The address of the cuboid.
		 */
		void cuboidDidUnload(CuboidAddress address);
		
		/**
		 * Called when the client's entity has loaded for the first time.
		 * Only called once per instance.
		 * 
		 * @param authoritativeEntity The entity state from the server.
		 */
		void thisEntityDidLoad(Entity authoritativeEntity);
		/**
		 * Called when the client's entity has changed (either due to server-originating changes or local changes).
		 * Called very frequently.
		 * 
		 * @param authoritativeEntity The entity state from the server.
		 * @param projectedEntity The client's local state (local changes applied to server data).
		 */
		void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity);
		
		/**
		 * Called when another entity is loaded for the first time.
		 * 
		 * @param entity The server's entity data.
		 */
		void otherEntityDidLoad(PartialEntity entity);
		/**
		 * Called when a previously-loaded entity's state changes.
		 * 
		 * @param entity The server's entity data.
		 */
		void otherEntityDidChange(PartialEntity entity);
		/**
		 * Called when another entity should be unloaded as the server is no longer sending us updates.
		 * 
		 * @param id The ID of the entity to unload.
		 */
		void otherEntityDidUnload(int id);
		/**
		 * Called when a game tick from the server has been fully processed.
		 * 
		 * @param gameTick The tick number (this is monotonic).
		 */
		void tickDidComplete(long gameTick);
		/**
		 * The server's config options were changed or it is announcing these right after connection.
		 * 
		 * @param ticksPerDay The number of ticks in a fully day cycle.
		 * @param dayStartTick The tick offset into ticksPerDay where the day "starts".
		 */
		void configUpdated(int ticksPerDay, int dayStartTick);
		/**
		 * Called when the server tells us another client has connected (or was connected when we joined).
		 * 
		 * @param clientId The ID of the other client.
		 * @param name The name of the other client.
		 */
		void otherClientJoined(int clientId, String name);
		/**
		 * Called when the server tells us another client has disconnected.
		 * 
		 * @param clientId The ID of the other client.
		 */
		void otherClientLeft(int clientId);
		/**
		 * Called when the server relays a chat message from another client.
		 * 
		 * @param senderId The ID of the other client (0 for "server console").
		 * @param message The message.
		 */
		void receivedChatMessage(int senderId, String message);
	}
}
