package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.LongConsumer;

import com.jeffdisher.october.changes.EndBreakBlockChange;
import com.jeffdisher.october.changes.EntityChangeMove;
import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
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
		_applyLocalChange(breakBlock, currentTimeMillis);
		_runAllPendingCalls(currentTimeMillis);
		return breakBlock.getTimeCostMillis();
	}

	/**
	 * Creates the change to move the entity from the current location in the speculative projection to the given
	 * endPoint.
	 * 
	 * @param endPoint The destination of the move.
	 * @param currentTimeMillis The current time, in milliseconds.
	 */
	public void moveTo(EntityLocation endPoint, long currentTimeMillis)
	{
		// We are going to base a decision on our current projection so make sure that anything in-progress completes, first.
		_projection.checkCurrentActivity(currentTimeMillis);
		
		// The caller shouldn't be asking us to move in ways which aren't possible (would imply the client's time behaviour is invalid).
		EntityLocation currentLocation = _localEntityProjection.location();
		// This would be a static usage or timing error on the client.
		Assert.assertTrue(EntityChangeMove.isValidMove(currentLocation, endPoint));
		
		EntityChangeMove moveChange = new EntityChangeMove(currentLocation, endPoint);
		_applyLocalChange(moveChange, currentTimeMillis);
		_runAllPendingCalls(currentTimeMillis);
	}


	private void _runAllPendingCalls(long currentTimeMillis)
	{
		List<LongConsumer> calls = _callsFromNetworkToApply.extractAllRunnables();
		while (!calls.isEmpty())
		{
			calls.remove(0).accept(currentTimeMillis);
		}
	}

	private void _applyLocalChange(IEntityChange change, long currentTimeMillis)
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


	private class NetworkListener implements IClientAdapter.IListener
	{
		// Since we get lots of small callbacks, we buffer them here, in the network thread, before passing back the
		// finished tick data (just avoids a lot of tiny calls between threads to perform the same trivial action).
		private List<Entity> _addedEntities = new ArrayList<>();
		private List<IReadOnlyCuboidData> _addedCuboids = new ArrayList<>();
		
		private Map<Integer, Queue<IEntityChange>> _entityChanges = new HashMap<>();
		private List<IMutation> _cuboidMutations = new ArrayList<>();
		
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
		public void receivedChange(int entityId, IEntityChange change)
		{
			Queue<IEntityChange> oneQueue = _entityChanges.get(entityId);
			if (null == oneQueue)
			{
				oneQueue = new LinkedList<>();
				_entityChanges.put(entityId, oneQueue);
			}
			oneQueue.add(change);
		}
		@Override
		public void receivedMutation(IMutation mutation)
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
			Map<Integer, Queue<IEntityChange>> entityChanges = new HashMap<>(_entityChanges);
			_entityChanges.clear();
			List<IMutation> cuboidMutations = new ArrayList<>(_cuboidMutations);
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
