package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
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
	// Read-only cuboids from the previous tick, resolved by address.
	private Map<CuboidAddress, IReadOnlyCuboidData> _completedCuboids;
	// Read-only entities from the previous tick, resolved by ID.
	private Map<Integer, Entity> _completedEntities;
	
	// Data which is part of "shared state" between external threads and the internal threads.
	private List<IMutation> _mutations;
	private List<CuboidData> _newCuboids;
	private List<IEntityChange> _newEntityChanges;
	private List<Entity> _newEntities;
	
	// Ivars which are related to the interlock where the threads merge partial results and wait to start again.
	private TickMaterials _thisTickMaterials;
	private final WorldProcessor.ProcessedFragment[] _partial;
	private final CrowdProcessor.ProcessedGroup[] _partialGroup;
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
	public TickRunner(int threadCount, WorldProcessor.IBlockChangeListener worldListener, CrowdProcessor.IEntityChangeListener entityListener)
	{
		// TODO:  Decide where to put the registry or how it should be used.
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_partial = new WorldProcessor.ProcessedFragment[threadCount];
		_partialGroup = new CrowdProcessor.ProcessedGroup[threadCount];
		_lastCompletedTick = -1;
		_nextTick = 0;
		_sharedDataLock = new ReentrantLock();
		for (int i = 0; i < threadCount; ++i)
		{
			// Create the loader for the read-only state (note that this is bound to us so it will see the most recent _completedCuboids).
			Function<AbsoluteLocation, BlockProxy> loader = (AbsoluteLocation location) -> {
				CuboidAddress address = location.getCuboidAddress();
				IReadOnlyCuboidData cuboid = _completedCuboids.get(address);
				return (null != cuboid)
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: null
				;
			};
			int id = i;
			_threads[i] = new Thread(() -> {
				ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
				_backgroundThreadMain(thisThread, loader, worldListener, entityListener);
			}, "Tick Runner #" + i);
		}
	}

	/**
	 * Starts the tick runner on an empty world but does 
	 */
	public void start()
	{
		Assert.assertTrue(null == _completedCuboids);
		Assert.assertTrue(null == _completedEntities);
		// Create an empty world - the cuboids will be asynchronously loaded.
		_completedCuboids = Collections.emptyMap();
		// Create it with no users.
		_completedEntities = Collections.emptyMap();
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
	public void cuboidWasLoaded(CuboidData cuboid)
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
		CuboidAddress address = location.getCuboidAddress();
		IReadOnlyCuboidData cuboid = _completedCuboids.get(address);
		
		BlockProxy block = null;
		if (null != cuboid)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = new BlockProxy(blockAddress, cuboid);
		}
		return block;
	}

	/**
	 * @param id The entity ID to look up.
	 * @return The read-only entity completed by the previous tick.
	 */
	public Entity getEntity(int id)
	{
		return _completedEntities.get(id);
	}


	private void _backgroundThreadMain(ProcessorElement thisThread
			, Function<AbsoluteLocation, BlockProxy> loader
			, WorldProcessor.IBlockChangeListener worldListener
			, CrowdProcessor.IEntityChangeListener entityListener
	)
	{
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, new WorldProcessor.ProcessedFragment(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList())
				, new CrowdProcessor.ProcessedGroup(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList())
				, Collections.emptyList()
				, Collections.emptyList()
		);
		while (null != materials)
		{
			// Run the tick.
			// Process all entity changes first and synchronize to lock-step.
			CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(thisThread, materials.completedEntities, entityListener, loader, materials.thisGameTick, materials.changesToRun);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != group);
			// Now, process the world changes.
			WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(thisThread, materials.completedCuboids, worldListener, loader, materials.thisGameTick, materials.mutationsToRun);
			// There is always a returned fragment (even if it has no content).
			Assert.assertTrue(null != fragment);
			materials = _mergeTickStateAndWaitForNext(thisThread
					, fragment
					, group
					, materials.newCuboidsToAdd
					, materials.newEntitiesToAdd
			);
		}
	}

	private TickMaterials _mergeTickStateAndWaitForNext(ProcessorElement elt
			, WorldProcessor.ProcessedFragment fragmentCompleted
			, CrowdProcessor.ProcessedGroup processedGroup
			, List<CuboidData> cuboidsToInject
			, List<Entity> entitiesToInject
	)
	{
		// Store whatever work we finished from the just-completed tick.
		_partial[elt.id] = fragmentCompleted;
		_partialGroup[elt.id] = processedGroup;
		
		// We synchronize threads here for a few reasons:
		// 1) We need to collect all the data from the just-finished frame and produce the updated immutable snapshot (this is a stitching operation so we do it on one thread).
		// 2) We need to wait for the next frame to be requested (we do this on one thread to simplify everything).
		// 3) We need to collect all the actions required for the next frame (this requires pulling data from the interlock).
		if (elt.synchronizeAndReleaseLast())
		{
			// Rebuild the immutable snapshot of the state.
			Map<CuboidAddress, IReadOnlyCuboidData> worldState = new HashMap<>();
			Map<Integer, Entity> crowdState = new HashMap<>();
			
			// Collect the end results into the combined world and crowd for the snapshot.
			// We start by adding the previous world state and writing the updates on top of it (note that this distinction could be used for unload logic, in the future).
			worldState.putAll(_completedCuboids);
			for (WorldProcessor.ProcessedFragment fragment : _partial)
			{
				worldState.putAll(fragment.stateFragment());
			}
			for (CuboidData cuboid : cuboidsToInject)
			{
				IReadOnlyCuboidData old = worldState.put(cuboid.getCuboidAddress(), cuboid);
				// This must not already be present.
				Assert.assertTrue(null == old);
			}
			// Similarly, start with the entities from last tick.
			crowdState.putAll(_completedEntities);
			for (CrowdProcessor.ProcessedGroup fragment : _partialGroup)
			{
				crowdState.putAll(fragment.groupFragment());
			}
			for (Entity entity : entitiesToInject)
			{
				Entity old = crowdState.put(entity.id(), entity);
				// This must not already be present.
				Assert.assertTrue(null == old);
			}
			_completedCuboids = Collections.unmodifiableMap(worldState);
			_completedEntities = Collections.unmodifiableMap(crowdState);
			
			// Now, wait for the next tick before we put together the materials.
			_acknowledgeTickCompleteAndWaitForNext();
			
			// We woke up so either run the next tick or exit (if the next tick was set negative, it means exit).
			if (_nextTick > 0)
			{
				// Load other cuboids and apply other mutations enqueued since the last tick.
				List<CuboidData> newCuboids;
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
				
				// Put together the materials for this tick
				Map<CuboidAddress, Queue<IMutation>> nextTickMutations = new HashMap<>();
				Map<Integer, Queue<IEntityChange>> nextTickChanges = new HashMap<>();
				
				// Next step is to schedule anything from the previous tick.
				for (WorldProcessor.ProcessedFragment fragment : _partial)
				{
					for (IMutation mutation : fragment.exportedMutations())
					{
						_scheduleMutationForCuboid(nextTickMutations, mutation);
					}
					for (IEntityChange change : fragment.exportedEntityChanges())
					{
						_scheduleChangeForEntity(nextTickChanges, change);
					}
				}
				for (CrowdProcessor.ProcessedGroup fragment : _partialGroup)
				{
					for (IMutation mutation : fragment.exportedMutations())
					{
						_scheduleMutationForCuboid(nextTickMutations, mutation);
					}
					for (IEntityChange change : fragment.exportedChanges())
					{
						_scheduleChangeForEntity(nextTickChanges, change);
					}
				}
				
				// Schedule the new mutations and changes (the new entities an cuboids are just passed through to when the final snapshot is assembled).
				if (null != newMutations)
				{
					for (IMutation mutation : newMutations)
					{
						_scheduleMutationForCuboid(nextTickMutations, mutation);
					}
				}
				if (null != newEntityChanges)
				{
					for (IEntityChange change : newEntityChanges)
					{
						_scheduleChangeForEntity(nextTickChanges, change);
					}
				}
				
				// TODO:  We should probably remove this once we are sure we know what is happening and/or find a cheaper way to check this.
				for (CuboidAddress key : nextTickMutations.keySet())
				{
					if (!worldState.containsKey(key))
					{
						System.out.println("WARNING: missing " + key);
					}
				}
				
				// Clear remaining hand-off state.
				for (int i = 0; i < _partial.length; ++i)
				{
					_partial[i] = null;
				}
				for (int i = 0; i < _partialGroup.length; ++i)
				{
					_partialGroup[i] = null;
				}
				
				// We now have a plan for this tick so save it in the ivar so the other threads can grab it.
				_thisTickMaterials = new TickMaterials(_nextTick
						, _completedCuboids
						, _completedEntities
						, nextTickMutations
						, nextTickChanges
						, (null != newCuboids) ? newCuboids : Collections.emptyList()
						, (null != newEntities) ? newEntities : Collections.emptyList()
				);
			}
			else
			{
				// Shut down.
				_thisTickMaterials = null;
			}
			// Now, we can release everyone and they will read _thisTickMaterials to see if we are still running.
			elt.releaseWaitingThreads();
		}
		
		return _thisTickMaterials;
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

	private void _scheduleMutationForCuboid(Map<CuboidAddress, Queue<IMutation>> nextTickMutations, IMutation mutation)
	{
		CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
		Queue<IMutation> queue = nextTickMutations.get(address);
		if (null == queue)
		{
			queue = new LinkedList<>();
			nextTickMutations.put(address, queue);
		}
		queue.add(mutation);
	}

	private void _scheduleChangeForEntity(Map<Integer, Queue<IEntityChange>> nextTickChanges, IEntityChange change)
	{
		int entityId = change.getTargetId();
		Queue<IEntityChange> queue = nextTickChanges.get(entityId);
		if (null == queue)
		{
			queue = new LinkedList<>();
			nextTickChanges.put(entityId, queue);
		}
		queue.add(change);
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


	private static record TickMaterials(long thisGameTick
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<Integer, Entity> completedEntities
			, Map<CuboidAddress, Queue<IMutation>> mutationsToRun
			, Map<Integer, Queue<IEntityChange>> changesToRun
			, List<CuboidData> newCuboidsToAdd
			, List<Entity> newEntitiesToAdd
	) {}
}
