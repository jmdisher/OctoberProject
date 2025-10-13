package com.jeffdisher.october.process;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.client.ClientRunner;
import com.jeffdisher.october.client.IClientAdapter;
import com.jeffdisher.october.client.IProjectionListener;
import com.jeffdisher.october.client.MovementAccumulator;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.net.CuboidCodec;
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
import com.jeffdisher.october.net.Packet_ClientUpdateOptions;
import com.jeffdisher.october.net.Packet_MutationEntityFromClient;
import com.jeffdisher.october.net.Packet_PartialEntity;
import com.jeffdisher.october.net.Packet_PartialEntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_ReceiveChatMessage;
import com.jeffdisher.october.net.Packet_EntityUpdateFromServer;
import com.jeffdisher.october.net.Packet_EventBlock;
import com.jeffdisher.october.net.Packet_EventEntity;
import com.jeffdisher.october.net.Packet_RemoveCuboid;
import com.jeffdisher.october.net.Packet_RemoveEntity;
import com.jeffdisher.october.net.Packet_RemovePassive;
import com.jeffdisher.october.net.Packet_SendChatMessage;
import com.jeffdisher.october.net.Packet_SendPartialPassive;
import com.jeffdisher.october.net.Packet_SendPartialPassiveUpdate;
import com.jeffdisher.october.net.Packet_ServerSendConfigUpdate;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
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
	private long _lastIncludedLocalCommit;
	private int _assignedClientId;
	private boolean _isEntityLoaded;
	private DisconnectException _disconnectException;

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
	 * @param cuboidViewDistance The client's preferred view distance.
	 * @throws IOException There was a network error connecting to the server.
	 */
	public ClientProcess(IListener listener, InetAddress address, int port, String clientName, int cuboidViewDistance) throws IOException
	{
		_listener = listener;
		_pendingCallbacks = new _LockingList();
		_clientRunner = new ClientRunner(new _NetworkAdapter(), new _ProjectionListener(), new _RunnerListener());
		// This should be set once the runner is started but before it returns.
		Assert.assertTrue(null != _messagesToClientRunner);
		
		// Create the connection (note that this will return when the connection is accepted but the handshake completes in the background).
		_client = new NetworkClient(new _NetworkClientListener(), address, port, clientName, cuboidViewDistance);
		
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
	public void sendAction(IEntitySubAction<IMutablePlayerEntity> change, long currentTimeMillis)
	{
		_clientRunner.commonApplyEntityAction(change, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Waits until this client's ID has been received with the end of the handshake.
	 * 
	 * @return The client's ID.
	 * @throws InterruptedException Interrupted while waiting.
	 * @throws DisconnectException The server disconnected.
	 */
	public synchronized int waitForClientId() throws InterruptedException, DisconnectException
	{
		while (0 == _assignedClientId)
		{
			if (null != _disconnectException)
			{
				throw _disconnectException;
			}
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
	 * @throws DisconnectException The server disconnected.
	 */
	public synchronized long waitForLocalEntity(long currentTimeMillis) throws InterruptedException, DisconnectException
	{
		// We want to wait until we see the end of at least a single tick since we don't want to return after the first
		// entity packet, but before the first tick packet (would give us 0).
		while ((0 == _lastTickFromServer) && (null == _disconnectException))
		{
			this.wait();
		}
		// Now, we can wait for the entity (although it likely came in before the tick).
		while (!_isEntityLoaded  && (null == _disconnectException))
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		if (null != _disconnectException)
		{
			throw _disconnectException;
		}
		return _lastTickFromServer;
	}

	/**
	 * Waits until the packet with the given tick number has been observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 * @throws DisconnectException The server disconnected.
	 */
	public synchronized long waitForTick(long tickNumber, long currentTimeMillis) throws InterruptedException, DisconnectException
	{
		while ((_lastTickFromServer < tickNumber) && (null == _disconnectException))
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		if (null != _disconnectException)
		{
			throw _disconnectException;
		}
		return _lastTickFromServer;
	}

	/**
	 * Waits until the packet with the given client commit level has been observed.
	 * NOTE:  Even though the packet has been received, this may return before it has been relayed to the listener as
	 * there is some buffering of those callbacks.
	 * 
	 * @return The last tick observed in a packet.
	 * @throws InterruptedException Interrupted while waiting.
	 * @throws DisconnectException The server disconnected.
	 */
	public synchronized long waitForLocalCommitInTick(long lastIncludedLocalCommit, long currentTimeMillis) throws InterruptedException, DisconnectException
	{
		while ((_lastIncludedLocalCommit < lastIncludedLocalCommit) && (null == _disconnectException))
		{
			this.wait();
		}
		_clientRunner.runPendingCalls(currentTimeMillis);
		_runPendingCallbacks();
		if (null != _disconnectException)
		{
			throw _disconnectException;
		}
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
	 * Sets the player's facing orientation.
	 * 
	 * @param yaw The left-right yaw.
	 * @param pitch The up-down pitch.
	 */
	public void setOrientation(byte yaw, byte pitch)
	{
		_clientRunner.setOrientation(yaw, pitch);
	}

	/**
	 * Creates the change to walk the entity from the current location in the speculative projection along the given
	 * horizontal direction for the amount of time which has passed since the last call.
	 * This will also apply z-acceleration for that amount of time and will handle cases such as collision but will at
	 * least attempt to move in this way (and will send the change to the server).
	 * 
	 * @param relativeDirection The direction to move, relative to current yaw.
	 * @param runningSpeed True if we should run, instead of walk.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void walk(MovementAccumulator.Relative relativeDirection, boolean runningSpeed, long currentTimeMillis)
	{
		_clientRunner.walk(relativeDirection, runningSpeed, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Similar to walk() but moves as a sneak:  Slower than walking but avoids slipping off of blocks.
	 * 
	 * @param relativeDirection The direction to move, relative to current yaw.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void sneak(MovementAccumulator.Relative relativeDirection, long currentTimeMillis)
	{
		_clientRunner.sneak(relativeDirection, currentTimeMillis);
		_runPendingCallbacks();
	}

	/**
	 * Allows time to pass to account for things like falling, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		_clientRunner.standStill(currentTimeMillis);
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
	 * Sends updated client options to the server.
	 * 
	 * @param clientViewDistance This client's viewable distance, in cuboids.
	 * @return True if the update was sent (false means the request was invalid and not sent).
	 */
	public boolean updateOptions(int clientViewDistance)
	{
		return _clientRunner.updateOptions(clientViewDistance);
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

	private synchronized void _background_updateTickNumber(long latestTickNumber, long lastIncludedLocalCommit)
	{
		Assert.assertTrue((0 == _lastTickFromServer) || ((_lastTickFromServer + 1) == latestTickNumber));
		_lastTickFromServer = latestTickNumber;
		_lastIncludedLocalCommit = lastIncludedLocalCommit;
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

	private synchronized void _background_serverDisconnected(DisconnectException exception)
	{
		_disconnectException = exception;
		this.notifyAll();
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
		public void handshakeCompleted(int assignedId, long millisPerTick, int currentViewDistance, int viewDistanceMaximum)
		{
			_messagesToClientRunner.adapterConnected(assignedId, millisPerTick, currentViewDistance, viewDistanceMaximum);
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
				_background_updateTickNumber(safe.tickNumber, safe.latestLocalCommitIncluded);
			}
			else if (packet instanceof Packet_RemoveEntity)
			{
				Packet_RemoveEntity safe = (Packet_RemoveEntity)packet;
				_messagesToClientRunner.removeEntity(safe.entityId);
			}
			else if (packet instanceof Packet_SendPartialPassive)
			{
				Packet_SendPartialPassive safe = (Packet_SendPartialPassive)packet;
				_messagesToClientRunner.receivedPassive(safe.partial);
			}
			else if (packet instanceof Packet_SendPartialPassiveUpdate)
			{
				Packet_SendPartialPassiveUpdate safe = (Packet_SendPartialPassiveUpdate)packet;
				_messagesToClientRunner.receivedPassiveUpdate(safe.entityId, safe.location, safe.velocity);
			}
			else if (packet instanceof Packet_RemovePassive)
			{
				Packet_RemovePassive safe = (Packet_RemovePassive)packet;
				_messagesToClientRunner.removePassive(safe.entityId);
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
			else if (packet instanceof Packet_EventBlock)
			{
				Packet_EventBlock safe = (Packet_EventBlock)packet;
				_messagesToClientRunner.receivedBlockEvent(safe.eventType, safe.location, safe.entitySourceId);
			}
			else if (packet instanceof Packet_EventEntity)
			{
				Packet_EventEntity safe = (Packet_EventEntity)packet;
				_messagesToClientRunner.receivedEntityEvent(safe.eventType, safe.cause, safe.optionalLocation, safe.entityTargetId, safe.entitySourceId);
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
			_background_serverDisconnected(new DisconnectException());
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
		public void sendChange(EntityActionSimpleMove<IMutablePlayerEntity> change, long commitLevel)
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
		@Override
		public void updateOptions(int clientViewDistance)
		{
			Assert.assertTrue(clientViewDistance >= 0);
			Packet_ClientUpdateOptions packet = new Packet_ClientUpdateOptions(clientViewDistance);
			_background_bufferPacket(packet);
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _ProjectionListener implements IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidLoad(cuboid, columnHeightMap);
			});
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, CuboidHeightMap cuboidHeightMap
				, ColumnHeightMap columnHeightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			_pendingCallbacks.add(() -> {
				_listener.cuboidDidChange(cuboid, columnHeightMap, changedBlocks, changedAspects);
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
		public void passiveEntityDidLoad(PartialPassive entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.passiveEntityDidLoad(entity);
			});
		}
		@Override
		public void passiveEntityDidChange(PartialPassive entity)
		{
			_pendingCallbacks.add(() -> {
				_listener.passiveEntityDidChange(entity);
			});
		}
		@Override
		public void passiveEntityDidUnload(int id)
		{
			_pendingCallbacks.add(() -> {
				_listener.passiveEntityDidUnload(id);
			});
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			_pendingCallbacks.add(() -> {
				_listener.tickDidComplete(gameTick);
			});
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			_pendingCallbacks.add(() -> {
				_listener.handleEvent(event);
			});
		}
	}

	// Note that these calls are issued on the thread which calls into the ClientRunner, meaning the thread we are treating as "main", from the user.
	private class _RunnerListener implements ClientRunner.IListener
	{
		@Override
		public void clientDidConnectAndLogin(int assignedLocalEntityId, int currentViewDistance)
		{
			_pendingCallbacks.add(() -> {
				_listener.connectionEstablished(assignedLocalEntityId, currentViewDistance);
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
		 * @param currentViewDistance The player's starting view distance (for populating UI, etc).
		 */
		void connectionEstablished(int assignedEntityId, int currentViewDistance);
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
		 * @param changedAspects The set of aspects changed by any of the changedBlocks.
		 */
		void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		);
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
		 * Called when a passive entity is loaded for the first time.
		 * 
		 * @param entity The server's entity data.
		 */
		void passiveEntityDidLoad(PartialPassive entity);
		/**
		 * Called when a previously-loaded passive's state changes.
		 * 
		 * @param entity The server's entity data.
		 */
		void passiveEntityDidChange(PartialPassive entity);
		/**
		 * Called when a passive should be unloaded as the server is no longer sending us updates.
		 * 
		 * @param id The ID of the entity to unload.
		 */
		void passiveEntityDidUnload(int id);

		/**
		 * Called when a game tick from the server has been fully processed.
		 * 
		 * @param gameTick The tick number (this is monotonic).
		 */
		void tickDidComplete(long gameTick);
		/**
		 * Called when an event is generated in the client or comes in from the server.
		 * 
		 * @param event The event.
		 */
		void handleEvent(EventRecord event);
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

	public static class DisconnectException extends Exception
	{
		private static final long serialVersionUID = 1L;
		public DisconnectException()
		{
			super("Server Disconnected");
		}
	}
}
