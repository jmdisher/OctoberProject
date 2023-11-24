package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.CrowdState.EntityWrapper;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.utils.Assert;


/**
 * The core of the multi-threaded logic.  This will advance the world state by one logical tick a time, when told to, by
 * using multiple threads to apply pending mutations to the previous versions of the world.
 * Once a tick is completed, that version of the world is considered committed as read-only.
 */
public class TickRunner
{
	private final SyncPoint _syncPoint;
	private final Thread[] _threads;
	private WorldState _completedWorld;
	private CrowdState _completedCrowd;
	
	// Data which is part of "shared state" between external threads and the internal threads.
	private List<IMutation> _mutations;
	private List<CuboidState> _newCuboids;
	private List<IEntityChange> _newEntityChanges;
	private List<Entity> _newEntities;
	
	private final WorldState.ProcessedFragment[] _partial;
	private final CrowdState.ProcessedGroup[] _partialGroup;
	private long _lastCompletedTick;
	private long _nextTick;
	// We use an explicit lock to guard shared data, instead of overloading the monitor, since the monitor shouldn't be used purely for data guards.
	private ReentrantLock _sharedDataLock;

	/**
	 * Creates the tick runner in a non-started state.
	 * 
	 * @param threadCount The number of threads to use to run the ticks.
	 * @param worldListener A listener for change events (note that these calls come on internal threads so must be trivial
	 * or hand-off to another thread).
	 * @param entityListener A listener for change events (note that these calls come on internal threads so must be trivial
	 * or hand-off to another thread).
	 */
	public TickRunner(int threadCount, WorldState.IBlockChangeListener worldListener, CrowdState.IEntityChangeListener entityListener)
	{
		// TODO:  Decide where to put the registry or how it should be used.
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_partial = new WorldState.ProcessedFragment[threadCount];
		_partialGroup = new CrowdState.ProcessedGroup[threadCount];
		_lastCompletedTick = -1;
		_nextTick = 0;
		_sharedDataLock = new ReentrantLock();
		for (int i = 0; i < threadCount; ++i)
		{
			int id = i;
			_threads[i] = new Thread(() -> {
				ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
				boolean keepRunning = _initialStartupPriming(thisThread);
				while (keepRunning)
				{
					// Run the tick.
					// Process all entity changes first and synchronize to lock-step.
					CrowdState.ProcessedGroup group = _completedCrowd.buildNewCrowdParallel(thisThread, entityListener);
					// There is always a returned group (even if it has no content).
					Assert.assertTrue(null != group);
					// Now, process the world changes.
					WorldState.ProcessedFragment fragment = _completedWorld.buildNewWorldParallel(thisThread, worldListener);
					// There is always a returned fragment (even if it has no content).
					Assert.assertTrue(null != fragment);
					keepRunning = _mergeTickStateAndWaitForNext(thisThread, fragment, group);
				}
			}, "Tick Runner #" + i);
		}
	}

	/**
	 * Starts the tick runner on an empty world but does 
	 */
	public void start()
	{
		Assert.assertTrue(null == _completedWorld);
		Assert.assertTrue(null == _completedCrowd);
		// Create an empty world - the cuboids will be asynchronously loaded.
		_completedWorld = new WorldState(Collections.emptyMap());
		// Create it with no users.
		_completedCrowd = new CrowdState(Collections.emptyMap());
		for (Thread thread : _threads)
		{
			thread.start();
		}
	}

	/**
	 * This will block until the previously requested tick has completed and all its threads have parked before
	 * returning.
	 */
	public synchronized void waitForPreviousTick()
	{
		// We just wait for the previous tick and don't start the next.
		_locked_waitForTickComplete();
	}

	/**
	 * Requests that another tick be run, waiting for the previous one to complete if it is still running.
	 * Note that this function returns before the next tick completes.
	 */
	public synchronized void startNextTick()
	{
		// Wait for the previous tick to complete.
		_locked_waitForTickComplete();
		// Advance to the next tick.
		_nextTick += 1;
		this.notifyAll();
	}

	/**
	 * Enqueues a mutation to be run in a future tick (it will be picked up in the current or next tick and run in the
	 * following tick).
	 * 
	 * @param mutation The mutation to enqueue.
	 */
	public void enqueueMutation(IMutation mutation)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _mutations)
			{
				_mutations = new ArrayList<>();
			}
			_mutations.add(mutation);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	/**
	 * Provides newly-loaded cuboid data to be loaded into the runner in a future tick (it will be picked up in the
	 * current or next tick).
	 * 
	 * @param cuboid The cuboid data to inject.
	 */
	public void cuboidWasLoaded(CuboidState cuboid)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _newCuboids)
			{
				_newCuboids = new ArrayList<>();
			}
			_newCuboids.add(cuboid);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	public void entityDidJoin(Entity entity)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _newEntities)
			{
				_newEntities = new ArrayList<>();
			}
			_newEntities.add(entity);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	public void enqueueEntityChange(IEntityChange change)
	{
		_sharedDataLock.lock();
		try
		{
			if (null == _newEntityChanges)
			{
				_newEntityChanges = new ArrayList<>();
			}
			_newEntityChanges.add(change);
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	/**
	 * Shuts down the tick runner.  Note that this will block until all runner threads have joined.
	 */
	public void shutdown()
	{
		// Notify them to shut down.
		synchronized (this)
		{
			// Wait until the previous tick is done.
			_locked_waitForTickComplete();
			// If the next tick is a negative number, the system will exit.
			_nextTick = -1;
			this.notifyAll();
		}
		
		// Now, join on everyone.
		for (Thread thread : _threads)
		{
			try
			{
				thread.join();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}

	/**
	 * Returns a read-only proxy for accessing all the data within a given block loaded within the current world state.
	 * Note that this proxy will only access the current version of the world state, so it shouldn't be cached outside
	 * of a single tick since it will only return that old data.
	 * 
	 * @param location The xyz location of the block.
	 * @return The block copy or null if the location isn't loaded.
	 */
	public BlockProxy getBlockProxy(AbsoluteLocation location)
	{
		return _completedWorld.getBlockProxy(location);
	}

	public Entity getEntity(int id)
	{
		return _completedCrowd.getEntity(id);
	}


	private boolean _initialStartupPriming(ProcessorElement elt)
	{
		// This is only called once during start-up in order to synchronize threads and allow an external caller to wait for start-up without a tick running.
		_partial[elt.id] = null;
		if (elt.synchronizeAndReleaseLast())
		{
			_acknowledgeTickCompleteAndWaitForNext();
			elt.releaseWaitingThreads();
		}
		return true;
	}

	private boolean _mergeTickStateAndWaitForNext(ProcessorElement elt, WorldState.ProcessedFragment fragmentCompleted, CrowdState.ProcessedGroup processedGroup)
	{
		// Store whatever work we finished from the just-completed tick.
		_partial[elt.id] = fragmentCompleted;
		_partialGroup[elt.id] = processedGroup;
		if (elt.synchronizeAndReleaseLast())
		{
			// All the data is stored so process it and wait for a request to run the next tick before releasing everyone.
			Map<CuboidAddress, CuboidState> worldState = new HashMap<>();
			for (WorldState.ProcessedFragment fragment : _partial)
			{
				worldState.putAll(fragment.stateFragment());
			}
			
			// Process the new entities, as well.
			Map<Integer, EntityWrapper> crowdState = new HashMap<>();
			for (CrowdState.ProcessedGroup fragment : _partialGroup)
			{
				crowdState.putAll(fragment.groupFragment());
			}
			
			// Load other cuboids and apply other mutations enqueued since the last tick.
			List<CuboidState> newCuboids;
			List<IMutation> newMutations;
			List<Entity> newEntities;
			List<IEntityChange> newEntityChanges;
			
			_sharedDataLock.lock();
			try
			{
				newCuboids = _newCuboids;
				_newCuboids = null;
				newMutations = _mutations;
				_mutations = null;
				newEntities = _newEntities;
				_newEntities = null;
				newEntityChanges = _newEntityChanges;
				_newEntityChanges = null;
			}
			finally
			{
				_sharedDataLock.unlock();
			}
			
			// Enqueue any new changes which came from existing changes.
			for (CrowdState.ProcessedGroup fragment : _partialGroup)
			{
				for (IEntityChange change : fragment.exportedChanges())
				{
					_scheduleChangeOnEntity(crowdState, change);
				}
			}
			
			// Enqueue any new changes which came from existing mutations.
			for (WorldState.ProcessedFragment fragment : _partial)
			{
				for (IEntityChange change : fragment.exportedEntityChanges())
				{
					_scheduleChangeOnEntity(crowdState, change);
				}
			}
			
			// Add the new entities.
			if (null != newEntities)
			{
				for (Entity entity : newEntities)
				{
					Object old = crowdState.put(entity.id(), new EntityWrapper(entity, new LinkedList<>()));
					Assert.assertTrue(null == old);
				}
			}
			
			// Add the new entity changes.
			if (null != newEntityChanges)
			{
				for (IEntityChange change : newEntityChanges)
				{
					_scheduleChangeOnEntity(crowdState, change);
				}
			}
			
			// First, add the new cuboids.
			if (null != newCuboids)
			{
				for (CuboidState cuboid : newCuboids)
				{
					CuboidAddress hash = cuboid.data.getCuboidAddress();
					CuboidState replaced = worldState.put(hash, cuboid);
					// This should NOT already be here.
					Assert.assertTrue(null == replaced);
				}
			}
			
			// Apply the mutations from internal operations.
			// -first from the entity changes
			for (CrowdState.ProcessedGroup fragment : _partialGroup)
			{
				for (IMutation mutation : fragment.exportedMutations())
				{
					_scheduleMutationOnCuboid(worldState, mutation);
				}
			}
			
			// -then from the block mutations
			for (WorldState.ProcessedFragment fragment : _partial)
			{
				for (IMutation mutation : fragment.exportedMutations())
				{
					_scheduleMutationOnCuboid(worldState, mutation);
				}
			}
			
			// Apply the other mutations which were enqueued since last tick completed and we parked.
			if (null != newMutations)
			{
				for (IMutation mutation : newMutations)
				{
					_scheduleMutationOnCuboid(worldState, mutation);
				}
			}
			
			// Replace the crowd with the new state.
			_completedCrowd = new CrowdState(crowdState);
			// Replace the world with the new state.
			_completedWorld = new WorldState(worldState);
			
			// Reset internal counters.
			for (int i = 0; i < _partial.length; ++i)
			{
				_partial[i] = null;
			}
			
			_acknowledgeTickCompleteAndWaitForNext();
			
			// Now, we can release everyone and they will read _nextTick to see if we are still running.
			elt.releaseWaitingThreads();
		}
		
		// If the next tick was set negative, it means exit.
		return (_nextTick > 0);
	}

	private synchronized void _acknowledgeTickCompleteAndWaitForNext()
	{
		// Acknowledge that the tick is completed.
		_lastCompletedTick = _nextTick;
		this.notifyAll();
		while (_lastCompletedTick == _nextTick)
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}

	private void _scheduleMutationOnCuboid(Map<CuboidAddress, CuboidState> worldState, IMutation mutation)
	{
		CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
		CuboidState cuboid = worldState.get(address);
		if (null != cuboid)
		{
			cuboid.enqueueMutation(mutation);
		}
		else
		{
			System.err.println("WARNING:  Mutation dropped due to missing cuboid");
		}
	}

	private void _scheduleChangeOnEntity(Map<Integer, EntityWrapper> crowdState, IEntityChange change)
	{
		EntityWrapper target = crowdState.get(change.getTargetId());
		// This would be a change coming in from a user which doesn't exist, which isn't possible (might change when leaving is implemented).
		Assert.assertTrue(null != target);
		target.changes().add(change);
	}

	private void _locked_waitForTickComplete()
	{
		while (_lastCompletedTick != _nextTick)
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// We don't use interruption.
				throw Assert.unexpected(e);
			}
		}
	}
}
