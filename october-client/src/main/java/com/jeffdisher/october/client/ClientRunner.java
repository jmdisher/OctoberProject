package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeDoNothing;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
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
	private final SpeculativeProjection.IProjectionListener _projectionListener;
	private final IListener _clientListener;
	private SpeculativeProjection _projection;
	private final List<Runnable> _pendingNetworkCallsToFlush;

	// Variables related to our local cache of this entity's state.
	private int _assignedEntityId;
	private Entity _localEntityProjection;

	// Variables related to moving calls from the network into the caller thread.
	private final LockedList _callsFromNetworkToApply;

	// We track the last time we called into the runner since time-sensitive changes need to know the time delta.
	// (failing to correctly set the delta will cause the server to block changes, meaning that the client could be disconnected due to flooding)
	private long _lastCallMillis;

	public ClientRunner(IClientAdapter network, SpeculativeProjection.IProjectionListener projectionListener, IListener clientListener)
	{
		_network = network;
		_projectionListener = projectionListener;
		_clientListener = clientListener;
		_pendingNetworkCallsToFlush = new ArrayList<>();
		
		_callsFromNetworkToApply = new LockedList();
		
		// This constructor probably does more than a constructor should (opening network connections) but this does give us a simple interface.
		NetworkListener networkListener = new NetworkListener();
		_network.connectAndStartListening(networkListener);
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
		_applyLocalChange(change);
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
			_applyLocalChange(hit);
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection by the given x/y
	 * fragments, multiplied by how far the entity can move since its last action.  The change will internally account
	 * for things like an existing z-vector, when falling or jumping, when building the final target location.
	 * Additionally, it will ignore the x and y movements if they aren't possible (hitting a wall), allowing any
	 * existing z movement to be handled.
	 * 
	 * @param xMultiple The fraction of the movement in the x-direction (usually -1.0, 0.0, or +1.0).
	 * @param yMultiple The fraction of the movement in the y-direction (usually -1.0, 0.0, or +1.0).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void moveHorizontalFully(float xMultiple, float yMultiple, long currentTimeMillis)
	{
		// Make sure that at least some time has passed.
		if (currentTimeMillis > _lastCallMillis)
		{
			// Make sure that the fractions of the movement are valid.
			Assert.assertTrue(1.0f == (Math.abs(xMultiple) + Math.abs(yMultiple)));
			
			long millisFree = Math.min(currentTimeMillis - _lastCallMillis, EntityChangeMove.LIMIT_COST_MILLIS);
			float secondsFree = (float)millisFree / 1000.0f;
			float speed = EntityConstants.SPEED_PLAYER;
			float distance = secondsFree * speed;
			Assert.assertTrue(EntityChangeMove.isValidDistance(speed, distance, 0.0f));
			
			// See if the horizontal movement is even feasible.
			EntityLocation previous = _localEntityProjection.location();
			EntityLocation validated = _findMovementTarget(xMultiple * distance, yMultiple * distance, previous);
			
			// Now, apply the move with validated the location (validated would be null if there is no way to partially reach our destination).
			long effectiveCurrentTimeMillis = currentTimeMillis;
			if (null != validated)
			{
				float thisX = validated.x() - previous.x();
				float thisY = validated.y() - previous.y();
				
				// Move to this location and update our last movement time accordingly (since it may not be the full time we calculated).
				long millisToMove = EntityChangeMove.getTimeMostMillis(speed, thisX, thisY);
				if (millisToMove > 0L)
				{
					EntityChangeMove<IMutablePlayerEntity> moveChange = new EntityChangeMove<>(speed, thisX, thisY);
					long missingMillis = (millisFree - millisToMove);
					effectiveCurrentTimeMillis -= missingMillis;
					_applyLocalChange(moveChange);
				}
			}
			else
			{
				// We can't move horizontally but see if maybe we should generate a do nothing action to fall.
				boolean isOnGround = SpatialHelpers.isStandingOnGround(_projection.projectionBlockLoader, previous, EntityConstants.VOLUME_PLAYER);
				if ((0.0f != _localEntityProjection.velocity().z())
					|| !isOnGround)
				{
					EntityChangeDoNothing<IMutablePlayerEntity> moveChange = new EntityChangeDoNothing<>(previous, millisFree);
					_applyLocalChange(moveChange);
				}
			}
			
			// Whether or not we did anything, run any pending calls while we are here.
			_runAllPendingCalls(effectiveCurrentTimeMillis);
			_lastCallMillis = effectiveCurrentTimeMillis;
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
			_applyLocalChange(craftOperation);
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
			else
			{
				// Nothing is happening so just account for passive movement, assuming there is any which makes sense.
				EntityLocation oldLocation = _localEntityProjection.location();
				boolean isOnGround = SpatialHelpers.isStandingOnGround(_projection.projectionBlockLoader, oldLocation, EntityConstants.VOLUME_PLAYER);
				if ((0.0f != _localEntityProjection.velocity().z())
						|| !isOnGround)
				{
					long doNothingTime = (millisToApply <= EntityChangeDoNothing.LIMIT_COST_MILLIS)
							? millisToApply
							: EntityChangeDoNothing.LIMIT_COST_MILLIS
					;
					EntityChangeDoNothing<IMutablePlayerEntity> moveChange = new EntityChangeDoNothing<>(oldLocation, doNothingTime);
					_applyLocalChange(moveChange);
				}
				// Whether or not we did anything, run any pending calls while we are here.
				_runAllPendingCalls(currentTimeMillis);
			}
			_lastCallMillis = currentTimeMillis;
		}
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

	private void _applyLocalChange(IMutationEntity<IMutablePlayerEntity> change)
	{
		long localCommit = _projection.applyLocalChange(change);
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
		_applyLocalChange(craftOperation);
		_runAllPendingCalls(currentTimeMillis);
	}

	private EntityLocation _findMovementTarget(float xDistance, float yDistance, EntityLocation previous)
	{
		EntityVolume volume = EntityConstants.VOLUME_PLAYER;
		EntityLocation validated = new EntityLocation(previous.x() + xDistance, previous.y() + yDistance, previous.z());
		while ((null != validated) && !SpatialHelpers.canExistInLocation(_projection.projectionBlockLoader, validated, volume))
		{
			// Adjust the coordinates.
			if (xDistance > 0.0f)
			{
				validated = SpatialHelpers.locationTouchingEastWall(_projection.projectionBlockLoader, validated, volume, previous.x());
			}
			else if (xDistance < 0.0f)
			{
				validated = SpatialHelpers.locationTouchingWestWall(_projection.projectionBlockLoader, validated, volume, previous.x());
			}
			else if (yDistance > 0.0f)
			{
				validated = SpatialHelpers.locationTouchingNorthWall(_projection.projectionBlockLoader, validated, volume, previous.y());
			}
			else if (yDistance < 0.0f)
			{
				validated = SpatialHelpers.locationTouchingSouthWall(_projection.projectionBlockLoader, validated, volume, previous.y());
			}
			else
			{
				validated = null;
			}
		}
		return validated;
	}


	private class NetworkListener implements IClientAdapter.IListener
	{
		// Since we get lots of small callbacks, we buffer them here, in the network thread, before passing back the
		// finished tick data (just avoids a lot of tiny calls between threads to perform the same trivial action).
		private Entity _thisEntity = null;
		private List<PartialEntity> _addedEntities = new ArrayList<>();
		private List<IReadOnlyCuboidData> _addedCuboids = new ArrayList<>();
		
		private List<IEntityUpdate> _entityUpdates = new ArrayList<>();
		private Map<Integer, List<IPartialEntityUpdate>> _partialEntityUpdates = new HashMap<>();
		private List<MutationBlockSetBlock> _cuboidUpdates = new ArrayList<>();
		
		private List<Integer> _removedEntities = new ArrayList<>();
		private List<CuboidAddress> _removedCuboids = new ArrayList<>();
		
		@Override
		public void adapterConnected(int assignedId)
		{
			_callsFromNetworkToApply.enqueue((long currentTimeMillis) -> {
				// We create the projection here.
				// We will locally wrap the projection listener we were given so that we will always know the properties of the entity.
				_assignedEntityId = assignedId;
				_projection = new SpeculativeProjection(assignedId, new LocalProjection());
				_lastCallMillis = currentTimeMillis;
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
			Assert.assertTrue(_assignedEntityId == entityId);
			_entityUpdates.add(update);
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
		public void receivedEndOfTick(long tickNumber, long latestLocalCommitIncluded)
		{
			// Package up copies of everything we put together here and reset out network-side buffers.
			Entity thisEntity = _thisEntity;
			_thisEntity = null;
			List<PartialEntity> addedEntities = new ArrayList<>(_addedEntities);
			_addedEntities.clear();
			List<IReadOnlyCuboidData> addedCuboids = new ArrayList<>(_addedCuboids);
			_addedCuboids.clear();
			List<IEntityUpdate> entityChanges = new ArrayList<>(_entityUpdates);
			_entityUpdates.clear();
			Map<Integer, List<IPartialEntityUpdate>> partialEntityChanges = new HashMap<>(_partialEntityUpdates);
			_partialEntityUpdates.clear();
			List<MutationBlockSetBlock> cuboidUpdates = new ArrayList<>(_cuboidUpdates);
			_cuboidUpdates.clear();
			List<Integer> removedEntities = new ArrayList<>(_removedEntities);
			_removedEntities.clear();
			List<CuboidAddress> removedCuboids = new ArrayList<>(_removedCuboids);
			_removedCuboids.clear();
			
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
						, entityChanges
						, partialEntityChanges
						, cuboidUpdates
						, removedEntities
						, removedCuboids
						, latestLocalCommitIncluded
						, currentTimeMillis
				);
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

	private class LocalProjection implements SpeculativeProjection.IProjectionListener
	{
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid)
		{
			// Ignored.
			_projectionListener.cuboidDidLoad(cuboid);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid)
		{
			// Ignored.
			_projectionListener.cuboidDidChange(cuboid);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			// Ignored.
			_projectionListener.cuboidDidUnload(address);
		}
		@Override
		public void thisEntityDidLoad(Entity entity)
		{
			_localEntityProjection = entity;
			_projectionListener.thisEntityDidLoad(entity);
		}
		@Override
		public void thisEntityDidChange(Entity entity)
		{
			_localEntityProjection = entity;
			_projectionListener.thisEntityDidChange(entity);
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
			// Just make sure that this isn't us (since that can't happen).
			Assert.assertTrue(_assignedEntityId != id);
			_projectionListener.otherEntityDidUnload(id);
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
	}
}
