package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeTimeSync;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * The top-level of the common data model and update system for October Project.  UI will exist outside of this but it
 * should otherwise be self-contained.
 * Note that it internally keeps track of changes coming in from the network layer, only applying them to the local
 * projection using a thread calling in to apply new changes or allow these changes to run.  Call-outs to given
 * listeners are also run at this time, on the calling thread.
 * Generally, either one of these calls should be issued by the external thread once per frame in order to allow any
 * changes to be observed.
 */
public class ClientRunner
{
	/**
	 * Most operations will only pass if at least this much time has passed.  This avoids corner-cases where some events
	 * might meaninglessly happen at the same time or the time interval is so small the packet would be a waste.
	 */
	public static final long TIME_GATE_MILLIS = 10L;

	private final IClientAdapter _network;
	private final IProjectionListener _projectionListener;
	private final IListener _clientListener;
	private SpeculativeProjection _projection;
	private final List<Runnable> _pendingNetworkCallsToFlush;

	// Variables related to our local cache of this entity's state.
	private Entity _localEntityProjection;

	// Variables related to moving calls from the network into the caller thread.
	private final LockedList _callsFromNetworkToApply;

	// We track the last time we called into the runner since time-sensitive changes need to know the time delta.
	// (failing to correctly set the delta will cause the server to block changes, meaning that the client could be disconnected due to flooding)
	private long _lastCallMillis;

	// This is set once when the client connects and is only read after that.
	private int _serverMaximumViewDistance;

	public ClientRunner(IClientAdapter network, IProjectionListener projectionListener, IListener clientListener)
	{
		_network = network;
		_projectionListener = projectionListener;
		_clientListener = clientListener;
		_pendingNetworkCallsToFlush = new ArrayList<>();
		
		_callsFromNetworkToApply = new LockedList();
		
		// This constructor probably does more than a constructor should (opening network connections) but this does give us a simple interface.
		NetworkListener networkListener = new NetworkListener();
		_network.connectAndStartListening(networkListener);
		
		_serverMaximumViewDistance = MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE;
	}

	/**
	 * The common path for applying changes which aren't exposed via other helpers in this interface.  Other helpers are
	 * used in cases where the change should be validated against the current projection state or depend on timing,
	 * since those can be handled internally to avoid pushing duplicated validation to the caller.
	 * 
	 * @param change The change to run.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void commonApplyEntityAction(IMutationEntity<IMutablePlayerEntity> change, long currentTimeMillis)
	{
		// Some of these events take no real time so we just pass them through, no matter how much time has passed.
		_applyLocalChange(change, currentTimeMillis);
		_runAllPendingCalls(currentTimeMillis);
		_lastCallMillis = currentTimeMillis;
	}

	/**
	 * Runs any pending call-outs.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void runPendingCalls(long currentTimeMillis)
	{
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
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
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			EntityChangeIncrementalBlockBreak hit = new EntityChangeIncrementalBlockBreak(blockLocation, (short)millisToApply);
			_applyLocalChange(hit, currentTimeMillis);
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
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
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			EntityChangeIncrementalBlockRepair repair = new EntityChangeIncrementalBlockRepair(blockLocation, (short)millisToApply);
			_applyLocalChange(repair, currentTimeMillis);
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
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
		// Make sure that at least some time has passed.
		if (currentTimeMillis > _lastCallMillis)
		{
			long millisFree = Math.min(currentTimeMillis - _lastCallMillis, EntityChangeMove.LIMIT_COST_MILLIS);
			
			// Move to this location and update our last movement time accordingly (since it may not be the full time we calculated).
			EntityChangeMove<IMutablePlayerEntity> moveChange = new EntityChangeMove<>(millisFree, 1.0f, direction);
			_applyLocalChange(moveChange, currentTimeMillis);
			
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
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
		// Make sure that at least some time has passed.
		if (currentTimeMillis > _lastCallMillis)
		{
			long millisFree = Math.min(currentTimeMillis - _lastCallMillis, EntityChangeAccelerate.LIMIT_COST_MILLIS);
			
			// Move to this location and update our last movement time accordingly (since it may not be the full time we calculated).
			EntityChangeAccelerate<IMutablePlayerEntity> accelerate = new EntityChangeAccelerate<>(millisFree, relativeDirection);
			_applyLocalChange(accelerate, currentTimeMillis);
			
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
	}

	/**
	 * Requests a crafting operation start.
	 * NOTE:  We will continue this in our "doNothing" calls.
	 * 
	 * @param operation The crafting operation to run.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void craft(Craft operation, long currentTimeMillis)
	{
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			_commonCraft(operation, currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
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
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			// We will account for how much time we have waited since the last action.
			EntityChangeCraftInBlock craftOperation = new EntityChangeCraftInBlock(block, operation, millisToApply);
			_applyLocalChange(craftOperation, currentTimeMillis);
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
	}

	/**
	 * Allows time to pass to account for things like falling, crafting, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		// Only apply this if it has been running for a while.
		if (millisToApply > TIME_GATE_MILLIS)
		{
			if (null != _localEntityProjection.localCraftOperation())
			{
				// We are crafting something, so continue with that.
				_commonCraft(_localEntityProjection.localCraftOperation().selectedCraft(), currentTimeMillis);
			}
			else if ((0.0f != _localEntityProjection.velocity().z()) || !SpatialHelpers.isStandingOnGround(_projection.projectionBlockLoader, _localEntityProjection.location(), Environment.getShared().creatures.PLAYER.volume()))
			{
				// We are passively moving (in a jump or falling) so create a time-passing mutation to keep us in sync with the server.
				EntityChangeTimeSync sync = new EntityChangeTimeSync(millisToApply);
				_applyLocalChange(sync, currentTimeMillis);
			}
			else
			{
				// If we are just standing still and not mid-craft, we don't even need to consult the projection since
				// nothing is moving (it only simulates time for the local entity).
			}
			_lastCallMillis = currentTimeMillis;
		}
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Sends a chat message to targetId.
	 * 
	 * @param targetId The ID of the target client (0 to mean "all").
	 * @param message The message to send.
	 */
	public void sentChatMessage(int targetId, String message)
	{
		Assert.assertTrue(targetId >= 0);
		Assert.assertTrue(null != message);
		_network.sendChatMessage(targetId, message);
	}

	/**
	 * Sends updated client options to the server.
	 * 
	 * @param clientViewDistance This client's viewable distance, in cuboids.
	 * @return True if the update was sent (false means the request was invalid and not sent).
	 */
	public boolean updateOptions(int clientViewDistance)
	{
		boolean didUpdate = false;
		Assert.assertTrue(clientViewDistance >= 0);
		if (clientViewDistance <= _serverMaximumViewDistance)
		{
			_network.updateOptions(clientViewDistance);
			didUpdate = true;
		}
		return didUpdate;
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
		_lastCallMillis = currentTimeMillis;
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Requests that this client disconnect from the server.
	 */
	public void disconnect()
	{
		_network.disconnect();
	}


	private void _runAllPendingCalls(long currentTimeMillis)
	{
		List<LongConsumer> calls = _callsFromNetworkToApply.extractAllRunnables();
		while (!calls.isEmpty())
		{
			calls.remove(0).accept(currentTimeMillis);
		}
	}

	private void _applyLocalChange(IMutationEntity<IMutablePlayerEntity> change, long currentTimeMillis)
	{
		long localCommit = _projection.applyLocalChange(change, currentTimeMillis);
		if (localCommit > 0L)
		{
			// This was applied locally so package it up to send to the server.  Currently, we will only flush network calls when we receive a new tick (but this will likely change).
			_pendingNetworkCallsToFlush.add(() -> {
				_network.sendChange(change, localCommit);
			});
		}
	}

	private void _commonCraft(Craft operation, long currentTimeMillis)
	{
		// We will account for how much time we have waited since the last action.
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		EntityChangeCraft craftOperation = new EntityChangeCraft(operation, millisToApply);
		_applyLocalChange(craftOperation, currentTimeMillis);
		_runAllPendingCalls(currentTimeMillis);
	}


	private class NetworkListener implements IClientAdapter.IListener
	{
		// Since we get lots of small callbacks, we buffer them here, in the network thread, before passing back the
		// finished tick data (just avoids a lot of tiny calls between threads to perform the same trivial action).
		private Entity _thisEntity = null;
		private List<PartialEntity> _addedEntities = new ArrayList<>();
		private List<IReadOnlyCuboidData> _addedCuboids = new ArrayList<>();
		
		private IEntityUpdate _entityUpdate = null;
		private Map<Integer, List<IPartialEntityUpdate>> _partialEntityUpdates = new HashMap<>();
		private List<MutationBlockSetBlock> _cuboidUpdates = new ArrayList<>();
		
		private List<Integer> _removedEntities = new ArrayList<>();
		private List<CuboidAddress> _removedCuboids = new ArrayList<>();
		
		private List<EventRecord> _events = new ArrayList<>();
		
		@Override
		public void adapterConnected(int assignedId, long millisPerTick, int viewDistanceMaximum)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				// We create the projection here.
				// We will locally wrap the projection listener we were given so that we will always know the properties of the entity.
				_projection = new SpeculativeProjection(assignedId, new LocalProjection(), millisPerTick);
				_lastCallMillis = currentTimeMillis;
				_serverMaximumViewDistance = viewDistanceMaximum;
				// Notify the listener that we were assigned an ID.
				_clientListener.clientDidConnectAndLogin(assignedId);
			});
		}
		@Override
		public void adapterDisconnected()
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				_clientListener.clientDisconnected();
			});
		}
		@Override
		public void receivedFullEntity(Entity entity)
		{
			// We currently assume that this is only this client, directly.
			Assert.assertTrue(null == _thisEntity);
			_thisEntity = entity;
		}
		@Override
		public void receivedPartialEntity(PartialEntity entity)
		{
			// Just add this to our local collection.
			_addedEntities.add(entity);
		}
		@Override
		public void removeEntity(int entityId)
		{
			_removedEntities.add(entityId);
		}
		@Override
		public void receivedCuboid(IReadOnlyCuboidData cuboid)
		{
			_addedCuboids.add(cuboid);
		}
		@Override
		public void removeCuboid(CuboidAddress address)
		{
			_removedCuboids.add(address);
		}
		@Override
		public void receivedEntityUpdate(int entityId, IEntityUpdate update)
		{
			// Currently (and probably forever), the only full entity on the client is the user, themselves.
			Assert.assertTrue(null == _entityUpdate);
			_entityUpdate = update;
		}
		@Override
		public void receivedPartialEntityUpdate(int entityId, IPartialEntityUpdate update)
		{
			List<IPartialEntityUpdate> oneQueue = _partialEntityUpdates.get(entityId);
			if (null == oneQueue)
			{
				oneQueue = new LinkedList<>();
				_partialEntityUpdates.put(entityId, oneQueue);
			}
			oneQueue.add(update);
		}
		@Override
		public void receivedBlockUpdate(MutationBlockSetBlock stateUpdate)
		{
			_cuboidUpdates.add(stateUpdate);
		}
		@Override
		public void receivedBlockEvent(EventRecord.Type type, AbsoluteLocation location, int entitySourceId)
		{
			EventRecord record = new EventRecord(type, EventRecord.Cause.NONE, location, 0, entitySourceId);
			_events.add(record);
		}
		@Override
		public void receivedEntityEvent(EventRecord.Type type, EventRecord.Cause cause, AbsoluteLocation optionalLocation, int entityTargetId, int entitySourceId)
		{
			EventRecord record = new EventRecord(type, cause, optionalLocation, entityTargetId, entitySourceId);
			_events.add(record);
		}
		@Override
		public void receivedEndOfTick(long tickNumber, long latestLocalCommitIncluded)
		{
			// Package up copies of everything we put together here and reset out network-side buffers.
			Entity thisEntity = _thisEntity;
			_thisEntity = null;
			List<PartialEntity> addedEntities = new ArrayList<>(_addedEntities);
			_addedEntities.clear();
			List<IReadOnlyCuboidData> addedCuboids = new ArrayList<>(_addedCuboids);
			_addedCuboids.clear();
			IEntityUpdate entityChange = _entityUpdate;
			_entityUpdate = null;
			Map<Integer, List<IPartialEntityUpdate>> partialEntityChanges = new HashMap<>(_partialEntityUpdates);
			_partialEntityUpdates.clear();
			List<MutationBlockSetBlock> cuboidUpdates = new ArrayList<>(_cuboidUpdates);
			_cuboidUpdates.clear();
			List<Integer> removedEntities = new ArrayList<>(_removedEntities);
			_removedEntities.clear();
			List<CuboidAddress> removedCuboids = new ArrayList<>(_removedCuboids);
			_removedCuboids.clear();
			List<EventRecord> events = new ArrayList<>(_events);
			_events.clear();
			
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				// Send anything we have outgoing.
				while (!_pendingNetworkCallsToFlush.isEmpty())
				{
					_pendingNetworkCallsToFlush.remove(0).run();
				}
				
				// Apply the changes from the server.
				if (null != thisEntity)
				{
					_projection.setThisEntity(thisEntity);
				}
				_projection.applyChangesForServerTick(tickNumber
						, addedEntities
						, addedCuboids
						, entityChange
						, partialEntityChanges
						, cuboidUpdates
						, removedEntities
						, removedCuboids
						, events
						, latestLocalCommitIncluded
						, currentTimeMillis
				);
			});
		}
		@Override
		public void receivedConfigUpdate(int ticksPerDay, int dayStartTick)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				_clientListener.configUpdated(ticksPerDay, dayStartTick);
			});
		}
		@Override
		public void receivedOtherClientJoined(int clientId, String name)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				_clientListener.otherClientJoined(clientId, name);
			});
		}
		@Override
		public void receivedOtherClientLeft(int clientId)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				_clientListener.otherClientLeft(clientId);
			});
		}
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				_clientListener.receivedChatMessage(senderId, message);
			});
		}
	}

	private class LockedList
	{
		// Since this is self-contained, we will just use the monitor, for brevity, even though an explicitly lock is technically more appropriate.
		private final List<LongConsumer> _calls = new LinkedList<>();
		
		public synchronized void enqueue(LongConsumer runnable)
		{
			_calls.add(runnable);
		}
		public synchronized List<LongConsumer> extractAllRunnables()
		{
			List<LongConsumer> copy = new LinkedList<>(_calls);
			_calls.clear();
			return copy;
		}
	}

	private class LocalProjection implements IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			// Ignored.
			_projectionListener.cuboidDidLoad(cuboid, heightMap);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			// Ignored.
			_projectionListener.cuboidDidChange(cuboid, heightMap, changedBlocks, changedAspects);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			// Ignored.
			_projectionListener.cuboidDidUnload(address);
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			// We will start with the authoritative data since their is no client-divergence, yet.
			_localEntityProjection = authoritativeEntity;
			_projectionListener.thisEntityDidLoad(authoritativeEntity);
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			// We only use the projected entity in this class since the authoritative is just for reporting stable numbers.
			_localEntityProjection = projectedEntity;
			_projectionListener.thisEntityDidChange(authoritativeEntity, projectedEntity);
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			_projectionListener.otherEntityDidLoad(entity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			_projectionListener.otherEntityDidChange(entity);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			_projectionListener.otherEntityDidUnload(id);
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			_projectionListener.tickDidComplete(gameTick);
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			_projectionListener.handleEvent(event);
		}
	}

	/**
	 * These callbacks are internally buffered, based on changes to the network state.  They are run on the caller
	 * thread when a method is called on ClientRunner.  This way, the client has complete control over when the
	 * callbacks are run and on which thread.
	 * Call "runPendingCalls(long)" to run any buffered callbacks if no action is to be taken.
	 */
	public interface IListener
	{
		/**
		 * The client has contacted the server and the handshake completed.
		 * 
		 * @param assignedLocalEntityId The ID assigned to this client by the server.
		 */
		void clientDidConnectAndLogin(int assignedLocalEntityId);
		/**
		 * The connection to the server has been closed by some external factor (NOT an explicit disconnect on the
		 * client side).  This would mean a server-initiated disconnect or a more general connection timeout.
		 */
		void clientDisconnected();
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
