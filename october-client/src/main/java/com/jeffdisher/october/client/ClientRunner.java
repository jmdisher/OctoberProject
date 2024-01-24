package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongConsumer;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EndBreakBlockChange;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.registries.Craft;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
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

	// We are responsible for managing concerns like jumping and falling, when packaging up the movement changes, here.
	private long _lastMoveMillis;

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
	 * Runs any pending call-outs.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void runPendingCalls(long currentTimeMillis)
	{
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Creates the change to begin breaking a block.  Note that changes which come in before it completes will
	 * invalidate it.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
	 * 
	 * @param blockLocation The location of the block to break.
	 * @param currentTimeMillis The current time, in milliseconds.
	 * @return The number of milliseconds this operation will take (meaning an explicit cancel should be sent if it
	 * shouldn't wait to complete).
	 */
	public long beginBreakBlock(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		// Send the end change since it has the appropriate delay (meaning we will need to 
		EndBreakBlockChange breakBlock = new EndBreakBlockChange(blockLocation, ItemRegistry.STONE.number());
		_applyLocalChange(breakBlock, currentTimeMillis, true);
		_runAllPendingCalls(currentTimeMillis);
		return breakBlock.getTimeCostMillis();
	}

	/**
	 * Creates the mutation to the entity to begin the sequence of operations to pick up items from an inventory block.
	 * Note that this CANNOT be called if there is still an in-progress activity running.  Call "isActivityInProgress()"
	 * first.
	 * 
	 * @param blockLocation The location of the block containing items.
	 * @param itemsToPull The items to transfer (actual item transfer could be smaller).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void pullItemsFromInventory(AbsoluteLocation blockLocation, Items itemsToPull, long currentTimeMillis)
	{
		// Start the multi-step process.
		MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(blockLocation, itemsToPull);
		_applyLocalChange(request, currentTimeMillis, false);
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Creates the mutation to the entity to begin the sequence of operations to put items into a block's inventory.
	 * Note that this CANNOT be called if there is still an in-progress activity running.  Call "isActivityInProgress()"
	 * first.
	 * 
	 * @param blockLocation The location of the block where the items should be stored.
	 * @param itemsToPush The items to transfer (actual item transfer could be smaller if they can't all fit).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void pushItemsToInventory(AbsoluteLocation blockLocation, Items itemsToPush, long currentTimeMillis)
	{
		MutationEntityPushItems push = new MutationEntityPushItems(blockLocation, itemsToPush);
		_applyLocalChange(push, currentTimeMillis, false);
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Changes the item selected by the current entity.
	 * 
	 * @param itemType The item type to select (will fail if not in their inventory).
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void selectItemInInventory(Item itemType, long currentTimeMillis)
	{
		// This is just a simple one.
		MutationEntitySelectItem select = new MutationEntitySelectItem(itemType);
		_applyLocalChange(select, currentTimeMillis, false);
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Places an instance of the item currently selected in the inventory in the world.
	 * 
	 * @param blockLocation The location where the block will be placed.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void placeSelectedBlock(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		// This is also relatively simple and is considered instant.
		MutationPlaceSelectedBlock place = new MutationPlaceSelectedBlock(blockLocation);
		_applyLocalChange(place, currentTimeMillis, false);
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection by the given x/y
	 * distances.  The change will internally account for things like an existing z-vector, when falling or jumping,
	 * when building the final target location.
	 * Additionally, it will ignore the x and y movements if they aren't possible (hitting a wall), allowing any
	 * existing z movement to be handled.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
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
		while ((null != validated) && !SpatialHelpers.canExistInLocation(_projection.shadowBlockLoader, validated, volume))
		{
			// Adjust the coordinates.
			if (xDistance > 0.0f)
			{
				validated = SpatialHelpers.locationTouchingEastWall(_projection.shadowBlockLoader, validated, volume, previous.x());
			}
			else if (xDistance < 0.0f)
			{
				validated = SpatialHelpers.locationTouchingWestWall(_projection.shadowBlockLoader, validated, volume, previous.x());
			}
			else if (yDistance > 0.0f)
			{
				validated = SpatialHelpers.locationTouchingNorthWall(_projection.shadowBlockLoader, validated, volume, previous.y());
			}
			else if (yDistance < 0.0f)
			{
				validated = SpatialHelpers.locationTouchingSouthWall(_projection.shadowBlockLoader, validated, volume, previous.y());
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
	}

	/**
	 * Makes the entity "jump", giving it a positive z-vector.
	 * Note that this CANNOT be called if there is still an in-progress activity running (as that could allow the move
	 * to be "from" a stale location).  Call "isActivityInProgress()" first.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void jump(long currentTimeMillis)
	{
		// We don't generate a move for passing time before jumping since we don't want to create something in-progress (and it would only matter if falling - in which the jump fails).
		EntityChangeJump jumpChange = new EntityChangeJump();
		_applyLocalChange(jumpChange, currentTimeMillis, true);
		_runAllPendingCalls(currentTimeMillis);
		// We don't set the last "move" time since a jump and a move are distinct.
	}

	/**
	 * Requests a crafting operation start.
	 * 
	 * @param operation The crafting operation to run.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void craft(Craft operation, long currentTimeMillis)
	{
		EntityChangeCraft craftOperation = new EntityChangeCraft(operation);
		_applyLocalChange(craftOperation, currentTimeMillis, true);
		_runAllPendingCalls(currentTimeMillis);
	}

	/**
	 * Allows time to pass to account for things like falling, etc.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void doNothing(long currentTimeMillis)
	{
		_commonMove(0.0f, 0.0f, currentTimeMillis);
	}

	/**
	 * Tries to complete any in-progress activity, returning true if it is still pending and false if it completed or
	 * there was nothing pending.
	 * 
	 * @param currentTimeMillis The current time, in milliseconds.
	 * @return True if there is still an in-progress activity pending.
	 */
	public boolean isActivityInProgress(long currentTimeMillis)
	{
		return _projection.checkCurrentActivity(currentTimeMillis);
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

	private void _applyLocalChange(IMutationEntity change, long currentTimeMillis, boolean canBeInProgress)
	{
		long localCommit = _projection.applyLocalChange(change, currentTimeMillis, canBeInProgress);
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
		long millisBeforeCall = (currentTimeMillis - _lastMoveMillis);
		// Make sure we have at least something to do.
		if ((millisBeforeCall > 0L) || (0.0f != xDistance) || (0.0f != yDistance))
		{
			// We assume that we spent time before this movement at least partially performing the move so update it.
			long millisToMove = EntityChangeMove.getTimeMostMillis(xDistance, yDistance);
			// We will skip any millis which don't fit (accounting for them all is ideal but the real-time nature of the client means we can miss some - especially during startup).
			if (millisToMove <= millisBeforeCall)
			{
				Assert.assertTrue(millisToMove <= EntityChangeMove.LIMIT_COST_MILLIS);
				long millisAbstractSlack = EntityChangeMove.LIMIT_COST_MILLIS - millisToMove;
				long millisRealSlack = millisBeforeCall - millisToMove;
				long millisBeforeMovement = (millisRealSlack > millisAbstractSlack) ? millisAbstractSlack : millisRealSlack;
				Assert.assertTrue(EntityChangeMove.isValidDistance(millisBeforeMovement, xDistance, yDistance));
				
				EntityChangeMove moveChange = new EntityChangeMove(_localEntityProjection.location(), millisBeforeMovement, xDistance, yDistance);
				_applyLocalChange(moveChange, currentTimeMillis, false);
				_runAllPendingCalls(currentTimeMillis);
				_lastMoveMillis = currentTimeMillis;
			}
		}
	}


	private class NetworkListener implements IClientAdapter.IListener
	{
		// Since we get lots of small callbacks, we buffer them here, in the network thread, before passing back the
		// finished tick data (just avoids a lot of tiny calls between threads to perform the same trivial action).
		private List<Entity> _addedEntities = new ArrayList<>();
		private List<IReadOnlyCuboidData> _addedCuboids = new ArrayList<>();
		
		private Map<Integer, Queue<IMutationEntity>> _entityChanges = new HashMap<>();
		private List<IMutationBlock> _cuboidMutations = new ArrayList<>();
		
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
				_lastMoveMillis = currentTimeMillis;
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
		public void receivedCuboid(IReadOnlyCuboidData cuboid)
		{
			_addedCuboids.add(cuboid);
		}
		@Override
		public void receivedChange(int entityId, IMutationEntity change)
		{
			Queue<IMutationEntity> oneQueue = _entityChanges.get(entityId);
			if (null == oneQueue)
			{
				oneQueue = new LinkedList<>();
				_entityChanges.put(entityId, oneQueue);
			}
			oneQueue.add(change);
		}
		@Override
		public void receivedMutation(IMutationBlock mutation)
		{
			_cuboidMutations.add(mutation);
		}
		@Override
		public void receivedEndOfTick(long tickNumber, long latestLocalCommitIncluded)
		{
			// Package up copies of everything we put together here and reset out network-side buffers.
			List<Entity> addedEntities = new ArrayList<>(_addedEntities);
			_addedEntities.clear();
			List<IReadOnlyCuboidData> addedCuboids = new ArrayList<>(_addedCuboids);
			_addedCuboids.clear();
			Map<Integer, Queue<IMutationEntity>> entityChanges = new HashMap<>(_entityChanges);
			_entityChanges.clear();
			List<IMutationBlock> cuboidMutations = new ArrayList<>(_cuboidMutations);
			_cuboidMutations.clear();
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
						, cuboidMutations
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
			// Ignored.
			_projectionListener.entityDidUnload(id);
		}
	}

	public interface IListener
	{
		void clientDidConnectAndLogin(int assignedLocalEntityId);
		void clientDisconnected();
	}
}
