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
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
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
	public void commonApplyEntityAction(IMutationEntity change, long currentTimeMillis)
	{
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
		_runAllPendingCalls(currentTimeMillis);
		_lastCallMillis = currentTimeMillis;
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
		if (millisToApply > 10L)
		{
			EntityChangeIncrementalBlockBreak hit = new EntityChangeIncrementalBlockBreak(blockLocation, (short)millisToApply);
			_applyLocalChange(hit, currentTimeMillis);
			_runAllPendingCalls(currentTimeMillis);
			_lastCallMillis = currentTimeMillis;
		}
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection by the given x/y
	 * distances.  The change will internally account for things like an existing z-vector, when falling or jumping,
	 * when building the final target location.
	 * Additionally, it will ignore the x and y movements if they aren't possible (hitting a wall), allowing any
	 * existing z movement to be handled.
	 * 
	 * @param xDistance How far to move in the x direction.
	 * @param yDistance How far to move in the y direction.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void moveHorizontal(float xDistance, float yDistance, long currentTimeMillis)
	{
		// See if the horizontal movement is even feasible.
		EntityLocation previous = _localEntityProjection.location();
		EntityVolume volume = _localEntityProjection.volume();
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
		
		// Now, apply the move with validated the location.
		float thisX = 0.0f;
		float thisY = 0.0f;
		if (null != validated)
		{
			thisX = validated.x() - previous.x();
			thisY = validated.y() - previous.y();
		}
		_commonMove(thisX, thisY, currentTimeMillis);
		_lastCallMillis = currentTimeMillis;
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
		_commonCraft(operation, currentTimeMillis);
		_lastCallMillis = currentTimeMillis;
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
		// We will account for how much time we have waited since the last action.
		long millisToApply = (currentTimeMillis - _lastCallMillis);
		EntityChangeCraftInBlock craftOperation = new EntityChangeCraftInBlock(block, operation, millisToApply);
		_applyLocalChange(craftOperation, currentTimeMillis);
		_runAllPendingCalls(currentTimeMillis);
		_lastCallMillis = currentTimeMillis;
	}

	/**
	 * Allows time to pass to account for things like falling, crafting, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		if (null != _localEntityProjection.localCraftOperation())
		{
			// We are crafting something, so continue with that.
			_commonCraft(_localEntityProjection.localCraftOperation().selectedCraft(), currentTimeMillis);
		}
		else
		{
			// Nothing is happening so just account for passive movement.
			_commonMove(0.0f, 0.0f, currentTimeMillis);
		}
		_lastCallMillis = currentTimeMillis;
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

	private void _applyLocalChange(IMutationEntity change, long currentTimeMillis)
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

	private void _commonMove(float xDistance, float yDistance, long currentTimeMillis)
	{
		EntityLocation oldLocation = _localEntityProjection.location();
		boolean isOnGround = SpatialHelpers.isStandingOnGround(_projection.projectionBlockLoader, oldLocation, _localEntityProjection.volume());
		// Make sure we have at least something to do:
		// -if we have any horizontal component
		// -if we have a z-vector (0.0 comparison should be ok since we assign it to that when stopped - not calculated)
		// -if we aren't standing on the ground
		if ((0.0f != xDistance) || (0.0f != yDistance)
				|| (0.0f != _localEntityProjection.zVelocityPerSecond())
				|| !isOnGround
		)
		{
			// We assume that we spent time before this movement at least partially performing the move so update it.
			long millisBeforeCall = (currentTimeMillis - _lastCallMillis);
			long millisToMove = EntityChangeMove.getTimeMostMillis(xDistance, yDistance);
			// We will skip any millis which don't fit (accounting for them all is ideal but the real-time nature of the client means we can miss some - especially during startup).
			if (millisToMove <= millisBeforeCall)
			{
				Assert.assertTrue(millisToMove <= EntityChangeMove.LIMIT_COST_MILLIS);
				long millisAbstractSlack = EntityChangeMove.LIMIT_COST_MILLIS - millisToMove;
				long millisRealSlack = millisBeforeCall - millisToMove;
				long millisBeforeMovement = (millisRealSlack > millisAbstractSlack) ? millisAbstractSlack : millisRealSlack;
				Assert.assertTrue(EntityChangeMove.isValidDistance(millisBeforeMovement, xDistance, yDistance));
				
				EntityChangeMove moveChange = new EntityChangeMove(oldLocation, millisBeforeMovement, xDistance, yDistance);
				_applyLocalChange(moveChange, currentTimeMillis);
			}
		}
		// Whether or not we did anything, run any pending calls while we are here.
		_runAllPendingCalls(currentTimeMillis);
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
		private List<Entity> _addedEntities = new ArrayList<>();
		private List<IReadOnlyCuboidData> _addedCuboids = new ArrayList<>();
		
		private Map<Integer, List<IMutationEntity>> _entityChanges = new HashMap<>();
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
		public void receivedEntity(Entity entity)
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
		public void receivedChange(int entityId, IMutationEntity change)
		{
			List<IMutationEntity> oneQueue = _entityChanges.get(entityId);
			if (null == oneQueue)
			{
				oneQueue = new LinkedList<>();
				_entityChanges.put(entityId, oneQueue);
			}
			oneQueue.add(change);
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
			List<Entity> addedEntities = new ArrayList<>(_addedEntities);
			_addedEntities.clear();
			List<IReadOnlyCuboidData> addedCuboids = new ArrayList<>(_addedCuboids);
			_addedCuboids.clear();
			Map<Integer, List<IMutationEntity>> entityChanges = new HashMap<>(_entityChanges);
			_entityChanges.clear();
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
				_projection.applyChangesForServerTick(tickNumber
						, addedEntities
						, addedCuboids
						, entityChanges
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
		public void entityDidLoad(Entity entity)
		{
			if (_assignedEntityId == entity.id())
			{
				_localEntityProjection = entity;
			}
			_projectionListener.entityDidLoad(entity);
		}
		@Override
		public void entityDidChange(Entity entity)
		{
			if (_assignedEntityId == entity.id())
			{
				_localEntityProjection = entity;
			}
			_projectionListener.entityDidChange(entity);
		}
		@Override
		public void entityDidUnload(int id)
		{
			// Just make sure that this isn't us (since that can't happen).
			Assert.assertTrue(_assignedEntityId != id);
			_projectionListener.entityDidUnload(id);
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
