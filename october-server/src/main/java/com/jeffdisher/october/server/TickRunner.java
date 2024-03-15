package com.jeffdisher.october.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IBlockStateUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.persistence.SuspendedCuboid;
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
	/**
	 * The maximum number of actions allowed waiting to be scheduled, per-client.  Attempting to go beyond this limit
	 * will disconnect the client.
	 * The rationale for this number is that we typically see 6 actions per tick (10 TPS with 60 FPS) which means that
	 * the slack should be at least 2 ticks and this gives us around 3.
	 */
	public static final int PENDING_ACTION_LIMIT = 20;

	private final SyncPoint _syncPoint;
	private final Thread[] _threads;
	private final long _millisPerTick;
	// Read-only snapshot of the previously-completed tick.
	private Snapshot _snapshot;
	
	// Data which is part of "shared state" between external threads and the internal threads.
	private List<SuspendedCuboid<IReadOnlyCuboidData>> _newCuboids;
	private Set<CuboidAddress> _cuboidsToDrop;
	private final Map<Integer, PerEntitySharedAccess> _entitySharedAccess;
	private List<Entity> _newEntities;
	private List<Integer> _departedEntityIds;
	
	// Ivars which are related to the interlock where the threads merge partial results and wait to start again.
	private TickMaterials _thisTickMaterials;
	private final _PartialHandoffData[] _partial;
	private long _nextTick;
	
	// We use an explicit lock to guard shared data, instead of overloading the monitor, since the monitor shouldn't be used purely for data guards.
	private ReentrantLock _sharedDataLock;

	/**
	 * Creates the tick runner in a non-started state.
	 * 
	 * @param threadCount The number of threads to use to run the ticks.
	 * @param millisPerTick The number of milliseconds to target for scheduling load within a tick.
	 * @param tickCompletionListener The consumer which we will given the completed snapshot of the state immediately before
	 * publishing the snapshot and blocking for the next tick (called on internal thread so must be trivial).
	 */
	public TickRunner(int threadCount
			, long millisPerTick
			, Consumer<Snapshot> tickCompletionListener
	)
	{
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_millisPerTick = millisPerTick;
		_entitySharedAccess = new HashMap<>();
		_partial = new _PartialHandoffData[threadCount];
		_nextTick = 1L;
		_sharedDataLock = new ReentrantLock();
		for (int i = 0; i < threadCount; ++i)
		{
			int id = i;
			_threads[i] = new Thread(() -> {
				try
				{
					ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
					_backgroundThreadMain(thisThread, tickCompletionListener);
				}
				catch (Throwable t)
				{
					// This is a fatal error so just stop.
					// We will manage this differently in the future but this makes test/debug turn-around simpler in the near-term.
					t.printStackTrace();
					System.exit(100);
				}
			}, "Tick Runner #" + i);
		}
	}

	/**
	 * Starts the tick runner.
	 */
	public void start()
	{
		Assert.assertTrue(null == _snapshot);
		// Initial snapshot is tick "0".
		_snapshot = new Snapshot(0L
				// Create an empty world - the cuboids will be asynchronously loaded.
				, Collections.emptyMap()
				// No commit levels.
				, Collections.emptyMap()
				// Create it with no users.
				, Collections.emptyMap()
				// No mutations, obviously.
				, Collections.emptyMap()
				, 0
				, Collections.emptyMap()
				, 0
				, Collections.emptyMap()
				, Collections.emptyMap()
		);
		for (Thread thread : _threads)
		{
			thread.start();
		}
	}

	/**
	 * This will block until the previously requested tick has completed and all its threads have parked before
	 * returning.
	 * @return Returns the snapshot of the now-completed tick.
	 */
	public synchronized Snapshot waitForPreviousTick()
	{
		// We just wait for the previous tick and don't start the next.
		return _locked_waitForTickComplete();
	}

	/**
	 * Requests that another tick be run, waiting for the previous one to complete if it is still running.
	 * Note that this function returns before the next tick completes.
	 * @return Returns the snapshot of the now-completed tick.
	 */
	public synchronized Snapshot startNextTick()
	{
		// Wait for the previous tick to complete.
		Snapshot snapshot = _locked_waitForTickComplete();
		// Advance to the next tick.
		_nextTick += 1;
		this.notifyAll();
		return snapshot;
	}

	/**
	 * Called in order to queue up changes to inject into the TickRunner before kicking-off a tick.  Technically, it is
	 * safe to call this at any time and the changes injected will be picked up during the next tick.
	 * Note that all changes injected this way will be reflected in the snapshot generated by the next started tick.
	 * 
	 * @param loadedCuboids The loaded cuboids to inject.
	 * @param cuboidsToUnload The cuboids to unload.
	 * @param loadedEntities The loaded entities to inject.
	 * @param entitiesToUnload The entities to unload.
	 */
	public void setupChangesForTick(Collection<SuspendedCuboid<IReadOnlyCuboidData>> loadedCuboids
			, Collection<CuboidAddress> cuboidsToUnload
			, Collection<Entity> loadedEntities
			, Collection<Integer> entitiesToUnload
	)
	{
		_sharedDataLock.lock();
		try
		{
			// We expect that this can only be called once per tick.
			Assert.assertTrue(null == _newCuboids);
			Assert.assertTrue(null == _cuboidsToDrop);
			Assert.assertTrue(null == _newEntities);
			Assert.assertTrue(null == _departedEntityIds);
			
			// Put these in local wrappers and update any special shared state required.
			_newCuboids = (null != loadedCuboids) ? new ArrayList<>(loadedCuboids) : null;
			_cuboidsToDrop = (null != cuboidsToUnload) ? new HashSet<>(cuboidsToUnload) : null;
			if (null != loadedEntities)
			{
				for (Entity entity : loadedEntities)
				{
					Assert.assertTrue(!_entitySharedAccess.containsKey(entity.id()));
					_entitySharedAccess.put(entity.id(), new PerEntitySharedAccess());
				}
				_newEntities = (null != loadedEntities) ? new ArrayList<>(loadedEntities) : null;
			}
			if (null != entitiesToUnload)
			{
				for (int entityId : entitiesToUnload)
				{
					Assert.assertTrue(_entitySharedAccess.containsKey(entityId));
					_entitySharedAccess.remove(entityId);
				}
				_departedEntityIds = (null != entitiesToUnload) ? new ArrayList<>(entitiesToUnload) : null;
			}
		}
		finally
		{
			_sharedDataLock.unlock();
		}
	}

	/**
	 * Enqueues a change to be scheduled on the given entityId.  Returns false if the entity should be disconnected.
	 * 
	 * @param entityId The entity where the change should be scheduled.
	 * @param change The change to schedule.
	 * @param commitLevel The client's commit level associated with this change.
	 * @return True if this was enqueued, false if the client should be disconnected.
	 */
	public boolean enqueueEntityChange(int entityId, IMutationEntity change, long commitLevel)
	{
		boolean didAdd;
		_sharedDataLock.lock();
		try
		{
			// Make sure that the entity isn't too far behind (has enqueued too many actions which aren't being run).
			PerEntitySharedAccess access = _entitySharedAccess.get(entityId);
			if (access.newChanges.size() < PENDING_ACTION_LIMIT)
			{
				access.newChanges.add(new _EntityMutationWrapper(change, commitLevel));
				didAdd = true;
			}
			else
			{
				// Disconnect the client by failing to add.
				didAdd = false;
			}
		}
		finally
		{
			_sharedDataLock.unlock();
		}
		return didAdd;
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


	private void _backgroundThreadMain(ProcessorElement thisThread
			, Consumer<Snapshot> tickCompletionListener
	)
	{
		// There is nothing loaded at the start so pass in an empty world and crowd state, as well as no work having been processed.
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, tickCompletionListener
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				, new WorldProcessor.ProcessedFragment(Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), 0)
				, new CrowdProcessor.ProcessedGroup(Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), 0)
		);
		while (null != materials)
		{
			// Run the tick.
			// Create the BlockProxy loader for the read-only state from the previous tick.
			final TickMaterials thisTickMaterials = materials;
			Function<AbsoluteLocation, BlockProxy> loader = (AbsoluteLocation location) -> {
				CuboidAddress address = location.getCuboidAddress();
				IReadOnlyCuboidData cuboid = thisTickMaterials.completedCuboids.get(address);
				return (null != cuboid)
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: null
				;
			};
			// Process all entity changes first and synchronize to lock-step.
			CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(thisThread, materials.completedEntities, loader, materials.thisGameTick, materials.changesToRun);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != group);
			// Now, process the world changes.
			WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(thisThread
					, materials.completedCuboids
					, loader
					, materials.thisGameTick
					, materials.mutationsToRun
					, materials.modifiedBlocksByCuboidAddress
					, materials.cuboidsLoadedThisTick
			);
			// There is always a returned fragment (even if it has no content).
			Assert.assertTrue(null != fragment);
			materials = _mergeTickStateAndWaitForNext(thisThread
					, tickCompletionListener
					, materials.completedCuboids
					, materials.completedEntities
					, materials.commitLevels
					, fragment
					, group
			);
		}
	}

	private TickMaterials _mergeTickStateAndWaitForNext(ProcessorElement elt
			, Consumer<Snapshot> tickCompletionListener
			, Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState
			, Map<Integer, Entity> mutableCrowdState
			, Map<Integer, Long> commitLevels
			, WorldProcessor.ProcessedFragment fragmentCompleted
			, CrowdProcessor.ProcessedGroup processedGroup
	)
	{
		// Store whatever work we finished from the just-completed tick.
		_partial[elt.id] = new _PartialHandoffData(fragmentCompleted, processedGroup, commitLevels);
		
		// We synchronize threads here for a few reasons:
		// 1) We need to collect all the data from the just-finished frame and produce the updated immutable snapshot (this is a stitching operation so we do it on one thread).
		// 2) We need to wait for the next frame to be requested (we do this on one thread to simplify everything).
		// 3) We need to collect all the actions required for the next frame (this requires pulling data from the interlock).
		if (elt.synchronizeAndReleaseLast())
		{
			// Rebuild the immutable snapshot of the state.
			Map<Integer, Long> combinedCommitLevels = new HashMap<>();
			// Stitch together the maps of completed mutations within this tick.
			Map<Integer, List<IMutationEntity>> resultantMutationsById = new HashMap<>();
			int committedEntityMutationCount = 0;
			Map<CuboidAddress, List<IBlockStateUpdate>> resultantMutationsByCuboid = new HashMap<>();
			int committedCuboidMutationCount = 0;
			
			// We will also capture any of the mutations which should be scheduled into the next tick since we should publish those into the snapshot.
			// (this is in case they need to be serialized - that way they can be read back in without interrupting the enqueued operations)
			Map<CuboidAddress, List<IMutationBlock>> scheduledBlockMutations = new HashMap<>();
			Map<Integer, List<IMutationEntity>> scheduledEntityMutations = new HashMap<>();
			
			for (int i = 0; i < _partial.length; ++i)
			{
				_PartialHandoffData fragment = _partial[i];
				// Collect the end results into the combined world and crowd for the snapshot (note that these are all replacing existing keys).
				mutableWorldState.putAll(fragment.world.stateFragment());
				// Similarly, collect the results of the changed entities for the snapshot.
				mutableCrowdState.putAll(fragment.crowd.groupFragment());
				// We will also collect all the per-client commit levels.
				combinedCommitLevels.putAll(fragment.commitLevels);
				// Collect the mutations.
				resultantMutationsById.putAll(fragment.crowd.resultantMutationsById());
				committedEntityMutationCount += fragment.crowd.committedMutationCount();
				resultantMutationsByCuboid.putAll(fragment.world.resultantMutationsByCuboid());
				committedCuboidMutationCount += fragment.world.committedMutationCount();
				
				// World data.
				for (IMutationBlock mutation : fragment.world.exportedMutations())
				{
					_scheduleMutationForCuboid(scheduledBlockMutations, mutation);
				}
				for (Map.Entry<Integer, List<IMutationEntity>> container : fragment.world.exportedEntityChanges().entrySet())
				{
					_scheduleChangesForEntity(scheduledEntityMutations, container.getKey(), container.getValue());
				}
				
				// Crowd data.
				for (IMutationBlock mutation : fragment.crowd.exportedMutations())
				{
					_scheduleMutationForCuboid(scheduledBlockMutations, mutation);
				}
				for (Map.Entry<Integer, List<IMutationEntity>> container : fragment.crowd.exportedChanges().entrySet())
				{
					_scheduleChangesForEntity(scheduledEntityMutations, container.getKey(), container.getValue());
				}
				
				_partial[i] = null;
			}
			
			// At this point, the tick to advance the world and crowd states has completed so publish the read-only results and wait before we put together the materials for the next tick.
			// Acknowledge that the tick is completed by creating a snapshot of the state.
			Snapshot completedTick = new Snapshot(_nextTick
					, Collections.unmodifiableMap(mutableCrowdState)
					, Collections.unmodifiableMap(combinedCommitLevels)
					, Collections.unmodifiableMap(mutableWorldState)
					, Collections.unmodifiableMap(resultantMutationsById)
					, committedEntityMutationCount
					, Collections.unmodifiableMap(resultantMutationsByCuboid)
					, committedCuboidMutationCount
					, Collections.unmodifiableMap(scheduledBlockMutations)
					, Collections.unmodifiableMap(scheduledEntityMutations)
			);
			// We want to pass this to a listener before we synchronize to avoid calling out under monitor.
			tickCompletionListener.accept(completedTick);
			_acknowledgeTickCompleteAndWaitForNext(completedTick);
			mutableWorldState = null;
			mutableCrowdState = null;
			
			// We woke up so either run the next tick or exit (if the next tick was set negative, it means exit).
			if (_nextTick > 0)
			{
				// Load other cuboids and apply other mutations enqueued since the last tick.
				List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids;
				Set<CuboidAddress> cuboidsToDrop;
				List<Entity> newEntities;
				List<Integer> removedEntityIds;
				Map<Integer, List<IMutationEntity>> newEntityChanges = new HashMap<>();
				Map<Integer, Long> newCommitLevels = new HashMap<>();
				
				_sharedDataLock.lock();
				try
				{
					newCuboids = _newCuboids;
					_newCuboids = null;
					cuboidsToDrop = _cuboidsToDrop;
					_cuboidsToDrop = null;
					newEntities = _newEntities;
					_newEntities = null;
					removedEntityIds = _departedEntityIds;
					_departedEntityIds = null;
					
					// We need to do some scheduling work under this lock.
					for (Map.Entry<Integer, PerEntitySharedAccess> entry : _entitySharedAccess.entrySet())
					{
						int id = entry.getKey();
						PerEntitySharedAccess access = entry.getValue();
						
						long schedulingBudget = _millisPerTick;
						List<IMutationEntity> queue = new LinkedList<>();
						
						long commitLevel = _sharedLock_ScheduleForEntity(access, queue, schedulingBudget);
						if (!queue.isEmpty())
						{
							newEntityChanges.put(id, queue);
							newCommitLevels.put(id, commitLevel);
						}
						else
						{
							// There may not be a previous commit level if this was just added.
							commitLevel = combinedCommitLevels.containsKey(id)
									? combinedCommitLevels.get(id)
									: 0L
							;
							newCommitLevels.put(id, commitLevel);
						}
					}
				}
				finally
				{
					_sharedDataLock.unlock();
				}
				
				// Put together the materials for this tick, starting with the new mutable world state and new mutations.
				Map<CuboidAddress, IReadOnlyCuboidData> nextWorldState = new HashMap<>();
				Map<Integer, Entity> nextCrowdState = new HashMap<>();
				Map<CuboidAddress, List<IMutationBlock>> nextTickMutations = new HashMap<>();
				
				// We don't currently have any "removal" concept so just start with a copy of what we created last tick.
				nextWorldState.putAll(_snapshot.completedCuboids);
				nextCrowdState.putAll(_snapshot.completedEntities);
				
				// Add in anything new.
				Set<CuboidAddress> cuboidsLoadedThisTick = new HashSet<>();
				if (null != newCuboids)
				{
					for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
					{
						IReadOnlyCuboidData cuboid = suspended.cuboid();
						CuboidAddress address = cuboid.getCuboidAddress();
						Object old = nextWorldState.put(address, cuboid);
						// This must not already be present.
						Assert.assertTrue(null == old);
						
						// Add any suspended mutations which came with the cuboid.
						List<IMutationBlock> mutations = suspended.mutations();
						if (!mutations.isEmpty())
						{
							old = nextTickMutations.put(address, new ArrayList<>(mutations));
							// This must not already be present (this was just created above here).
							Assert.assertTrue(null == old);
						}
						cuboidsLoadedThisTick.add(address);
					}
				}
				if (null != newEntities)
				{
					for (Entity entity : newEntities)
					{
						Entity old = nextCrowdState.put(entity.id(), entity);
						// This must not already be present.
						Assert.assertTrue(null == old);
					}
				}
				
				// Add any of the mutations scheduled from the last tick.
				for (List<IMutationBlock> list : scheduledBlockMutations.values())
				{
					for (IMutationBlock mutation : list)
					{
						_scheduleMutationForCuboid(nextTickMutations, mutation);
					}
				}
				scheduledBlockMutations = null;
				
				// Remove anything old.
				if (null != cuboidsToDrop)
				{
					for (CuboidAddress address : cuboidsToDrop)
					{
						IReadOnlyCuboidData old = nextWorldState.remove(address);
						// This must already be present.
						Assert.assertTrue(null != old);
						
						// Remove any of the scheduled operations for this cuboid.
						nextTickMutations.remove(address);
					}
				}
				if (null != removedEntityIds)
				{
					for (int entityId : removedEntityIds)
					{
						Entity old = nextCrowdState.remove(entityId);
						// This must have been present.
						Assert.assertTrue(null != old);
					}
				}
				Map<Integer, List<IMutationEntity>> nextTickChanges = new HashMap<>();
				for (Map.Entry<Integer, List<IMutationEntity>> entry : scheduledEntityMutations.entrySet())
				{
					// We can't modify the original so use a new container.
					Object prev = nextTickChanges.put(entry.getKey(), new LinkedList<>(entry.getValue()));
					// This is the first time constructing this so it must be empty.
					Assert.assertTrue(null == prev);
				}
				scheduledEntityMutations = null;
				for (Map.Entry<Integer, List<IMutationEntity>> container : newEntityChanges.entrySet())
				{
					_scheduleChangesForEntity(nextTickChanges, container.getKey(), container.getValue());
				}
				
				// TODO:  We should probably remove this once we are sure we know what is happening and/or find a cheaper way to check this.
				for (CuboidAddress key : nextTickMutations.keySet())
				{
					if (!nextWorldState.containsKey(key))
					{
						System.out.println("WARNING: missing cuboid " + key);
					}
				}
				for (Integer id : nextTickChanges.keySet())
				{
					if (!nextCrowdState.containsKey(id))
					{
						System.out.println("WARNING: missing entity " + id);
					}
				}
				
				// We want to build the arrangement of blocks modified in the last tick so that block updates can be synthesized.
				Map<CuboidAddress, List<AbsoluteLocation>> updatedBlockLocationsByCuboid = new HashMap<>();
				for (Map.Entry<CuboidAddress, List<IBlockStateUpdate>> entry : resultantMutationsByCuboid.entrySet())
				{
					List<AbsoluteLocation> locations = entry.getValue().stream().map(
							(IBlockStateUpdate update) -> update.getAbsoluteLocation()
					).toList();
					updatedBlockLocationsByCuboid.put(entry.getKey(), locations);
				}
				
				// We now have a plan for this tick so save it in the ivar so the other threads can grab it.
				_thisTickMaterials = new TickMaterials(_nextTick
						, nextWorldState
						, nextCrowdState
						, nextTickMutations
						, nextTickChanges
						, updatedBlockLocationsByCuboid
						, cuboidsLoadedThisTick
						
						// Data only used by this method:
						, newCommitLevels
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

	private synchronized void _acknowledgeTickCompleteAndWaitForNext(Snapshot newSnapshot)
	{
		_snapshot = newSnapshot;
		this.notifyAll();
		while (_snapshot.tickNumber == _nextTick)
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

	private void _scheduleMutationForCuboid(Map<CuboidAddress, List<IMutationBlock>> nextTickMutations, IMutationBlock mutation)
	{
		CuboidAddress address = mutation.getAbsoluteLocation().getCuboidAddress();
		List<IMutationBlock> queue = nextTickMutations.get(address);
		if (null == queue)
		{
			queue = new LinkedList<>();
			nextTickMutations.put(address, queue);
		}
		queue.add(mutation);
	}

	private void _scheduleChangesForEntity(Map<Integer, List<IMutationEntity>> nextTickChanges, int entityId, List<IMutationEntity> changes)
	{
		List<IMutationEntity> queue = nextTickChanges.get(entityId);
		if (null == queue)
		{
			nextTickChanges.put(entityId, changes);
		}
		else
		{
			queue.addAll(changes);
		}
	}

	private Snapshot _locked_waitForTickComplete()
	{
		while (_snapshot.tickNumber != _nextTick)
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
		return _snapshot;
	}

	// Returns the commit level of the last mutation scheduled on the entity (0 if nothing scheduled).
	private long _sharedLock_ScheduleForEntity(PerEntitySharedAccess access, List<IMutationEntity> scheduledQueue, long schedulingBudget)
	{
		long commitLevel = 0L;
		// First, check if this next change is a cancellation (-1 cost).
		_EntityMutationWrapper next = access.newChanges.peek();
		if (null != next)
		{
			long delayMillis = next.mutation.getTimeCostMillis();
			if (-1L == delayMillis)
			{
				// We are consuming this so remove it.
				access.newChanges.remove();
				// If there was something in-progress, drop it and run the cancellation instead (just so that we will update the commit level through the common path in the caller).
				if (null != access.inProgress)
				{
					scheduledQueue.add(next.mutation);
					commitLevel = next.commitLevel;
					access.inProgress = null;
				}
				// Advance to the next for scheduling decisions.
				next = access.newChanges.peek();
			}
		}
		
		// Next, check the in-progress to see if we can schedule it.
		if (null != access.inProgress)
		{
			if (access.millisUntilInProgressExecution <= schedulingBudget)
			{
				// Schedule the waiting change.
				schedulingBudget -= access.millisUntilInProgressExecution;
				scheduledQueue.add(access.inProgress.mutation);
				commitLevel = access.inProgress.commitLevel;
				access.inProgress = null;
			}
			else
			{
				// Decrement the time remaining and consume the budget.
				access.millisUntilInProgressExecution -= schedulingBudget;
				schedulingBudget = 0L;
			}
		}
		
		// Schedule anything else which fits in the budget.
		while ((null != next) && (schedulingBudget > 0L))
		{
			long cost = next.mutation.getTimeCostMillis();
			if (cost <= schedulingBudget)
			{
				// Just schedule this.
				scheduledQueue.add(next.mutation);
				commitLevel = next.commitLevel;
				schedulingBudget -= cost;
			}
			else
			{
				// Make this in-progress.
				access.inProgress = next;
				access.millisUntilInProgressExecution = cost - schedulingBudget;
				schedulingBudget = 0L;
			}
			// We have consumed this.
			access.newChanges.remove();
			next = access.newChanges.peek();
		}
		return commitLevel;
	}


	/**
	 * The snapshot of immutable state created whenever a tick is completed.
	 */
	public static record Snapshot(long tickNumber
			// Read-only entities from the previous tick, resolved by ID.
			, Map<Integer, Entity> completedEntities
			, Map<Integer, Long> commitLevels
			// Read-only cuboids from the previous tick, resolved by address.
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			// Note that the resultantMutationsById may not be the input mutations, but will have an equivalent impact on the crowd.
			, Map<Integer, List<IMutationEntity>> resultantMutationsById
			, int committedEntityMutationCount
			// Note that the resultantMutationsByCuboid may not be the input mutations, but will have an equivalent impact on the world.
			, Map<CuboidAddress, List<IBlockStateUpdate>> resultantMutationsByCuboid
			, int committedCuboidMutationCount
			// These fields are related to what is scheduled for the _next_ tick (added here to expose them to serialization).
			, Map<CuboidAddress, List<IMutationBlock>> scheduledBlockMutations
			, Map<Integer, List<IMutationEntity>> scheduledEntityMutations
	)
	{}

	private static record TickMaterials(long thisGameTick
			// Read-only versions of the cuboids produced by the previous tick (by address).
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			// Read-only versions of the Entities produced by the previous tick (by ID).
			, Map<Integer, Entity> completedEntities
			// The block mutations to run in this tick (by cuboid address).
			, Map<CuboidAddress, List<IMutationBlock>> mutationsToRun
			// The entity mutations to run in this tick (by ID).
			, Map<Integer, List<IMutationEntity>> changesToRun
			// The blocks modified in the last tick, represented as a list per cuboid where they originate.
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			// The set of addresses loaded in this tick (they are present in this tick, but for the first time).
			, Set<CuboidAddress> cuboidsLoadedThisTick
			
			// ----- TickRunner private state attached to the materials below this line -----
			// The last commit levels of all connected clients (by ID).
			, Map<Integer, Long> commitLevels
	) {}

	/**
	 * The per-entity data shared between foreground and background threads for scheduling changes.
	 * 
	 * In-progress changes are those which are blocking progress through the queue of changes from a given client.
	 * If the next change for the client is one with -1 cost (a cancellation), then this change is aborted as failed.
	 * Otherwise, the millis remaining are counted down, with each tick, until 0, at which point the change is scheduled
	 * to run.
	 */
	private static class PerEntitySharedAccess
	{
		private final Queue<_EntityMutationWrapper> newChanges = new LinkedList<>();
		private _EntityMutationWrapper inProgress;
		private long millisUntilInProgressExecution;
	}

	/**
	 * A wrapper over the IMutationEntity to store commit level data.
	 */
	private static record _EntityMutationWrapper(IMutationEntity mutation, long commitLevel) {}

	/**
	 * A wrapper over the per-thread partial data which we hand-off at synchronization.
	 */
	private static record _PartialHandoffData(WorldProcessor.ProcessedFragment world
			, CrowdProcessor.ProcessedGroup crowd
			, Map<Integer, Long> commitLevels
	) {}
}
