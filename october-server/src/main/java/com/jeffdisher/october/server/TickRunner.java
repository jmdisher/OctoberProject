package com.jeffdisher.october.server;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.CreatureProcessor;
import com.jeffdisher.october.logic.CreatureSpawner;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IEntityAction;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.persistence.SuspendedEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
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
	 * will disconnect the client due to flooding concerns.
	 * Since the only injected changes can be EntityChangeTopLevelMovement, and those all occupy a full tick, this limit
	 * maps to how many ticks out of the sync the client can be and then catch up in a burst.  We will set this number
	 * to 20, as that would map to 1 second, based on default parameters.
	 */
	public static final int PENDING_ACTION_LIMIT = 20;

	private final SyncPoint _syncPoint;
	private final Thread[] _threads;
	private final long _millisPerTick;
	private final CreatureIdAssigner _idAssigner;
	private final IntUnaryOperator _random;
	// Read-only snapshot of the previously-completed tick.
	private Snapshot _snapshot;
	
	// Data which is part of "shared state" between external threads and the internal threads.
	private List<SuspendedCuboid<IReadOnlyCuboidData>> _newCuboids;
	private Set<CuboidAddress> _cuboidsToDrop;
	private final Map<Integer, PerEntitySharedAccess> _entitySharedAccess;
	private List<SuspendedEntity> _newEntities;
	private List<Integer> _departedEntityIds;
	private List<_OperatorMutationWrapper> _operatorMutations;
	
	// Ivars which are related to the interlock where the threads merge partial results and wait to start again.
	private TickMaterials _thisTickMaterials;
	private final _PartialHandoffData[] _partial;
	private final ProcessorElement.PerThreadStats[] _threadStats;
	private long _nextTick;
	
	// We use an explicit lock to guard shared data, instead of overloading the monitor, since the monitor shouldn't be used purely for data guards.
	private ReentrantLock _sharedDataLock;

	/**
	 * Creates the tick runner in a non-started state.
	 * 
	 * @param threadCount The number of threads to use to run the ticks.
	 * @param millisPerTick The number of milliseconds to target for scheduling load within a tick.
	 * @param idAssigner The assigner for spawning new creatures.
	 * @param randomInt A random generator producing values in the range of [0..bound) for a given bound.
	 * @param tickCompletionListener The consumer which we will given the completed snapshot of the state immediately before
	 * publishing the snapshot and blocking for the next tick (called on internal thread so must be trivial).
	 * @param difficulty The difficulty configuration of the server.
	 */
	public TickRunner(int threadCount
			, long millisPerTick
			, CreatureIdAssigner idAssigner
			, IntUnaryOperator randomInt
			, Consumer<Snapshot> tickCompletionListener
			, WorldConfig config
	)
	{
		AtomicInteger atomic = new AtomicInteger(0);
		_syncPoint = new SyncPoint(threadCount);
		_threads = new Thread[threadCount];
		_millisPerTick = millisPerTick;
		_idAssigner = idAssigner;
		_random = randomInt;
		_entitySharedAccess = new HashMap<>();
		_partial = new _PartialHandoffData[threadCount];
		_threadStats = new ProcessorElement.PerThreadStats[threadCount];
		_nextTick = 1L;
		_sharedDataLock = new ReentrantLock();
		for (int i = 0; i < threadCount; ++i)
		{
			int id = i;
			_threads[i] = new Thread(() -> {
				try
				{
					ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
					_backgroundThreadMain(thisThread
							, tickCompletionListener
							, config
					);
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
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				
				, Collections.emptyMap()
				
				// postedEvents
				, List.of()
				
				// Information related to tick behaviour and performance statistics.
				, new TickStats(0L
						, 0L
						, 0L
						, 0L
						, null
						, 0
						, 0
				)
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
			, Collection<SuspendedEntity> loadedEntities
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
				for (SuspendedEntity suspended : loadedEntities)
				{
					Entity entity = suspended.entity();
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
	public boolean enqueueEntityChange(int entityId, EntityChangeTopLevelMovement<IMutablePlayerEntity> change, long commitLevel)
	{
		// TODO:  We should validate these parameters closer to the decoding point.
		Assert.assertTrue(entityId > 0);
		Assert.assertTrue(commitLevel > 0L);
		
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
	 * Enqueues a locally-originating (server console) mutation to change the given entityId for scheduling in the next
	 * tick.
	 * 
	 * @param entityId The entity where the change should be scheduled (CrowdProcessor.OPERATOR_ENTITY_ID for no entity).
	 * @param change The change to schedule.
	 */
	public void enqueueOperatorMutation(int entityId, IEntityAction<IMutablePlayerEntity> change)
	{
		// The entity might be missing, but the ID should be positive or OPERATOR_ENTITY_ID.
		Assert.assertTrue((entityId > 0) || (CrowdProcessor.OPERATOR_ENTITY_ID == entityId));
		
		_sharedDataLock.lock();
		try
		{
			if (null == _operatorMutations)
			{
				_operatorMutations = new ArrayList<>();
			}
			_operatorMutations.add(new _OperatorMutationWrapper(entityId, change));
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


	private void _backgroundThreadMain(ProcessorElement thisThread
			, Consumer<Snapshot> tickCompletionListener
			, WorldConfig config
	)
	{
		// There is nothing loaded at the start so pass in an empty world and crowd state, as well as no work having been processed.
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, tickCompletionListener
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				, Collections.emptyMap()
				// mutableCreatureState
				, Collections.emptyMap()
				, new _PartialHandoffData(new WorldProcessor.ProcessedFragment(Map.of(), Map.of(), List.of(), Map.of(), Map.of(), 0)
						, new CrowdProcessor.ProcessedGroup(0, Map.of(), Map.of())
						, new CreatureProcessor.CreatureGroup(false, Map.of(), List.of())
						, List.of()
						, List.of()
						, Map.of()
						, Map.of()
						, List.of()
						, Map.of()
				)
				, 0L
				, System.currentTimeMillis()
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
			// WARNING:  This block cache is used for everything this thread does and we may want to provide a flushing mechanism.
			LazyLocationCache<BlockProxy> cachingLoader = new LazyLocationCache<>(loader);
			CommonMutationSink newMutationSink = new CommonMutationSink();
			CommonChangeSink newChangeSink = new CommonChangeSink();
			List<EventRecord> events = new ArrayList<>();
			
			// We will capture the newly-spawned creatures into a basic list.
			List<CreatureEntity> spawnedCreatures = new ArrayList<>();
			TickProcessingContext.ICreatureSpawner spawnConsumer = (EntityType type, EntityLocation location, byte health) -> {
				int id = _idAssigner.next();
				CreatureEntity entity = CreatureEntity.create(id, type, location, health);
				spawnedCreatures.add(entity);
			};
			
			// On the server, we just generate the tick time as purely abstract monotonic value.
			long currentTickTimeMillis = (materials.thisGameTick * _millisPerTick);
			TickProcessingContext context = new TickProcessingContext(materials.thisGameTick
					, cachingLoader
					, (Integer entityId) -> (entityId > 0)
						? MinimalEntity.fromEntity(thisTickMaterials.completedEntities.get(entityId))
						: MinimalEntity.fromCreature(thisTickMaterials.completedCreatures.get(entityId))
					, (AbsoluteLocation blockLocation) -> {
						CuboidColumnAddress column = blockLocation.getCuboidAddress().getColumn();
						BlockAddress blockAddress = blockLocation.getBlockAddress();
						ColumnHeightMap map = thisTickMaterials.completedHeightMaps.get(column);
						
						byte skyLight;
						if (null != map)
						{
							int highestBlock = map.getHeight(blockAddress.x(), blockAddress.y());
							// If this is the highest block, return the light, otherwise 0.
							skyLight = (blockLocation.z() == highestBlock)
									? PropagationHelpers.currentSkyLightValue(thisTickMaterials.thisGameTick, config.ticksPerDay, config.dayStartTick)
									: 0
							;
						}
						else
						{
							// Note that the map may be null if this is just during start-up so just say that the light is 0, in that case.
							skyLight = 0;
						}
						return skyLight;
					}
					, newMutationSink
					, newChangeSink
					, spawnConsumer
					, _random
					, (EventRecord event) -> events.add(event)
					, config
					, _millisPerTick
					, currentTickTimeMillis
			);
			EntityCollection entityCollection = new EntityCollection(thisTickMaterials.completedEntities, thisTickMaterials.completedCreatures);
			
			// We will have the first thread attempt the monster spawning algorithm.
			if (thisThread.handleNextWorkUnit())
			{
				// This will spawn in the context, if spawning is appropriate.
				CreatureSpawner.trySpawnCreature(context
						, entityCollection
						, materials.completedCuboids
						, materials.completedHeightMaps
						, materials.completedCreatures
				);
			}
			
			long startCreatures = System.currentTimeMillis();
			CreatureProcessor.CreatureGroup creatureGroup = CreatureProcessor.processCreatureGroupParallel(thisThread
					, materials.completedCreatures
					, context
					, entityCollection
					, materials.creatureChanges
			);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != creatureGroup);
			
			long startCrowd = System.currentTimeMillis();
			CrowdProcessor.ProcessedGroup group = CrowdProcessor.processCrowdGroupParallel(thisThread
					, materials.completedEntities
					, context
					, materials.changesToRun
			);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != group);
			// Now, process the world changes.
			long startWorld = System.currentTimeMillis();
			WorldProcessor.ProcessedFragment fragment = WorldProcessor.processWorldFragmentParallel(thisThread
					, materials.completedCuboids
					, context
					, materials.mutationsToRun
					, materials.periodicMutationMillis
					, materials.modifiedBlocksByCuboidAddress
					, materials.potentialLightChangesByCuboid
					, materials.potentialLogicChangesByCuboid
					, materials.cuboidsLoadedThisTick
			);
			// There is always a returned fragment (even if it has no content).
			Assert.assertTrue(null != fragment);
			long endLoop = System.currentTimeMillis();
			
			// Update our thread stats before we merge.
			thisThread.millisInCreatureProcessor = (startCrowd - startCreatures);
			thisThread.millisInCrowdProcessor = (startWorld - startCrowd);
			thisThread.millisInWorldProcessor = (endLoop - startWorld);
			materials = _mergeTickStateAndWaitForNext(thisThread
					, tickCompletionListener
					, materials.completedCuboids
					, materials.cuboidHeightMaps
					, materials.completedHeightMaps
					, materials.completedEntities
					, materials.completedCreatures
					, new _PartialHandoffData(fragment
							, group
							, creatureGroup
							, spawnedCreatures
							, newMutationSink.takeExportedMutations()
							, newChangeSink.takeExportedChanges()
							, newChangeSink.takeExportedCreatureChanges()
							, events
							, materials.commitLevels
					)
					, materials.millisInTickPreamble
					, materials.timeMillisPreambleEnd
			);
		}
	}

	private TickMaterials _mergeTickStateAndWaitForNext(ProcessorElement elt
			, Consumer<Snapshot> tickCompletionListener
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<CuboidAddress, CuboidHeightMap> completedCuboidHeightMap
			, Map<CuboidColumnAddress, ColumnHeightMap> completedColumnHeightMaps
			, Map<Integer, Entity> completedCrowdState
			, Map<Integer, CreatureEntity> completedCreatureState
			, _PartialHandoffData perThreadData
			
			// Data related to internal statistics generated at the end of the previous tick and passed back in.
			, long millisInTickPreamble
			, long timeMillisPreambleEnd
	)
	{
		// Store whatever work we finished from the just-completed tick.
		_partial[elt.id] = perThreadData;
		_threadStats[elt.id] = elt.consumeAndResetStats();
		
		// We synchronize threads here for a few reasons:
		// 1) We need to collect all the data from the just-finished frame and produce the updated immutable snapshot (this is a stitching operation so we do it on one thread).
		// 2) We need to wait for the next frame to be requested (we do this on one thread to simplify everything).
		// 3) We need to collect all the actions required for the next frame (this requires pulling data from the interlock).
		if (elt.synchronizeAndReleaseLast())
		{
			long startMillisPostamble = System.currentTimeMillis();
			// Rebuild the immutable snapshot of the state.
			Map<Integer, Long> combinedCommitLevels = new HashMap<>();
			Map<Integer, Entity> updatedEntities = new HashMap<>();
			Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
			int committedEntityMutationCount = 0;
			Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
			int committedCuboidMutationCount = 0;
			
			// We will also capture any of the mutations which should be scheduled into the next tick since we should publish those into the snapshot.
			// (this is in case they need to be serialized - that way they can be read back in without interrupting the enqueued operations)
			Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = new HashMap<>();
			Map<CuboidAddress, Map<BlockAddress, Long>> snapshotPeriodicMutations = new HashMap<>();
			Map<Integer, List<ScheduledChange>> snapshotEntityMutations = new HashMap<>();
			Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> nextCreatureChanges = new HashMap<>();
			List<EventRecord> postedEvents = new ArrayList<>();
			
			// We will create new mutable maps from the previous materials and modify them based on the changes in the fragments.
			// We will create read-only snapshots for the Snapshot object and continue to modify these in order to create the next TickMaterials.
			Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState = new HashMap<>(completedCuboids);
			Map<CuboidAddress, CuboidHeightMap> mergedChangedHeightMaps = new HashMap<>();
			Map<Integer, Entity> mutableCrowdState = new HashMap<>(completedCrowdState);
			Map<Integer, CreatureEntity> mutableCreatureState = new HashMap<>(completedCreatureState);
			
			for (int i = 0; i < _partial.length; ++i)
			{
				_PartialHandoffData fragment = _partial[i];
				
				// Collect the end results into the combined world and crowd for the snapshot (note that these are all replacing existing keys).
				mutableWorldState.putAll(fragment.world.stateFragment());
				mergedChangedHeightMaps.putAll(fragment.world.heightFragment());
				// Similarly, collect the results of the changed entities for the snapshot.
				Map<Integer, Entity> entitiesChangedInFragment = fragment.crowd.updatedEntities();
				Map<Integer, CreatureEntity> creaturesChangedInFragment = fragment.creatures.updatedCreatures();
				List<CreatureEntity> creaturesSpawnedInFragment = fragment.spawnedCreatures();
				List<Integer> creaturesKilledInFragment = fragment.creatures.deadCreatureIds();
				mutableCrowdState.putAll(entitiesChangedInFragment);
				// Creatures are like entities, but in their own collection.
				mutableCreatureState.putAll(creaturesChangedInFragment);
				for (Integer creatureId : creaturesKilledInFragment)
				{
					mutableCreatureState.remove(creatureId);
				}
				for (CreatureEntity newCreature : creaturesSpawnedInFragment)
				{
					mutableCreatureState.put(newCreature.id(), newCreature);
				}
				
				// We will also collect all the per-client commit levels.
				combinedCommitLevels.putAll(fragment.commitLevels);
				updatedEntities.putAll(entitiesChangedInFragment);
				updatedCreatures.putAll(creaturesChangedInFragment);
				committedEntityMutationCount += fragment.crowd.committedMutationCount();
				blockChangesByCuboid.putAll(fragment.world.blockChangesByCuboid());
				committedCuboidMutationCount += fragment.world.committedMutationCount();
				
				// Common data given to the context.
				for (ScheduledMutation scheduledMutation : fragment.newlyScheduledMutations)
				{
					_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
				}
				for (Map.Entry<Integer, List<ScheduledChange>> container : fragment.newlyScheduledChanges().entrySet())
				{
					_scheduleChangesForEntity(snapshotEntityMutations, container.getKey(), container.getValue());
				}
				for (Map.Entry<Integer, List<IEntityAction<IMutableCreatureEntity>>> container : fragment.newlyScheduledCreatureChanges().entrySet())
				{
					_scheduleChangesForEntity(nextCreatureChanges, container.getKey(), container.getValue());
				}
				postedEvents.addAll(fragment.postedEvents);
				
				// World data.
				for (ScheduledMutation scheduledMutation : fragment.world.notYetReadyMutations())
				{
					_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
				}
				for (Map.Entry<CuboidAddress, Map<BlockAddress, Long>> ent : fragment.world.periodicNotReadyByCuboid().entrySet())
				{
					Map<BlockAddress, Long> old = snapshotPeriodicMutations.put(ent.getKey(), ent.getValue());
					// These should never overlap.
					Assert.assertTrue(null == old);
				}
				
				// Crowd data.
				for (Map.Entry<Integer, List<ScheduledChange>> container : fragment.crowd.notYetReadyChanges().entrySet())
				{
					_scheduleChangesForEntity(snapshotEntityMutations, container.getKey(), container.getValue());
				}
				
				_partial[i] = null;
			}
			
			// We want to extract the block state set objects from the block change information for the snapshot.
			Map<CuboidAddress, List<MutationBlockSetBlock>> resultantBlockChangesByCuboid = new HashMap<>();
			for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> perCuboid : blockChangesByCuboid.entrySet())
			{
				List<MutationBlockSetBlock> list = perCuboid.getValue().stream()
						.map((BlockChangeDescription description) -> description.serializedForm())
						.toList();
				resultantBlockChangesByCuboid.put(perCuboid.getKey(), list);
			}
			Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.rebuildColumnMaps(completedColumnHeightMaps
					, completedCuboidHeightMap
					, mergedChangedHeightMaps
					, mutableWorldState.keySet()
			);
			long endMillisPostamble = System.currentTimeMillis();
			long millisTickParallelPhase = (startMillisPostamble - timeMillisPreambleEnd);
			long millisTickPostamble = (endMillisPostamble - startMillisPostamble);
			
			// ***************** Tick ends here *********************
			
			// At this point, the tick to advance the world and crowd states has completed so publish the read-only results and wait before we put together the materials for the next tick.
			// Acknowledge that the tick is completed by creating a snapshot of the state.
			Map<CuboidAddress, SnapshotCuboid> cuboids = new HashMap<>();
			for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> ent : mutableWorldState.entrySet())
			{
				CuboidAddress key = ent.getKey();
				IReadOnlyCuboidData cuboid = ent.getValue();
				
				// The list of block changes will be null if nothing changed but the list of mutations will never be null, although typically empty.
				List<MutationBlockSetBlock> changedBlocks = resultantBlockChangesByCuboid.get(key);
				Assert.assertTrue((null == changedBlocks) || !changedBlocks.isEmpty());
				List<ScheduledMutation> scheduledMutations = snapshotBlockMutations.get(key);
				if (null == scheduledMutations)
				{
					scheduledMutations = List.of();
				}
				Map<BlockAddress, Long> periodicMutationMillis = snapshotPeriodicMutations.get(key);
				if (null == periodicMutationMillis)
				{
					periodicMutationMillis = Map.of();
				}
				SnapshotCuboid snapshot = new SnapshotCuboid(
						cuboid
						, changedBlocks
						, scheduledMutations
						, periodicMutationMillis
				);
				cuboids.put(key, snapshot);
			}
			Map<Integer, SnapshotEntity> entities = new HashMap<>();
			for (Map.Entry<Integer, Entity> ent : mutableCrowdState.entrySet())
			{
				Integer key = ent.getKey();
				Assert.assertTrue(key > 0);
				Entity completed = ent.getValue();
				long commitLevel = combinedCommitLevels.get(key);
				Entity updated = updatedEntities.get(key);
				
				// Get the scheduled mutations (note that this is often null but we don't want to store null).
				List<ScheduledChange> scheduledMutations = snapshotEntityMutations.get(key);
				if (null == scheduledMutations)
				{
					scheduledMutations = List.of();
				}
				SnapshotEntity snapshot = new SnapshotEntity(
						completed
						, commitLevel
						, updated
						, scheduledMutations
				);
				entities.put(key, snapshot);
			}
			Map<Integer, SnapshotCreature> creatures = new HashMap<>();
			for (Map.Entry<Integer, CreatureEntity>  ent : mutableCreatureState.entrySet())
			{
				Integer key = ent.getKey();
				Assert.assertTrue(key < 0);
				CreatureEntity completed = ent.getValue();
				CreatureEntity visiblyChanged = updatedCreatures.get(key);
				
				SnapshotCreature snapshot = new SnapshotCreature(
						completed
						, visiblyChanged
				);
				creatures.put(key, snapshot);
			}
			
			Snapshot completedTick = new Snapshot(_nextTick
					, Collections.unmodifiableMap(cuboids)
					, Collections.unmodifiableMap(entities)
					, Collections.unmodifiableMap(creatures)
					, completedHeightMaps
					
					// postedEvents
					, postedEvents
					
					// Stats.
					, new TickStats(_nextTick
						, millisInTickPreamble
						, millisTickParallelPhase
						, millisTickPostamble
						, _threadStats.clone()
						, committedEntityMutationCount
						, committedCuboidMutationCount
					)
			);
			
			// We want to pass this to a listener before we synchronize to avoid calling out under monitor.
			tickCompletionListener.accept(completedTick);
			
			// This is the point where we will block for the next tick to be requested.
			_acknowledgeTickCompleteAndWaitForNext(completedTick);
			
			// ***************** Tick starts here *********************
			
			long startMillisPreamble = System.currentTimeMillis();
			
			// We woke up so either run the next tick or exit (if the next tick was set negative, it means exit).
			if (_nextTick > 0)
			{
				// Load other cuboids and apply other mutations enqueued since the last tick.
				List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids;
				Set<CuboidAddress> cuboidsToDrop;
				List<SuspendedEntity> newEntities;
				List<Integer> removedEntityIds;
				List<_OperatorMutationWrapper> operatorMutations;
				Map<Integer, EntityChangeTopLevelMovement<IMutablePlayerEntity>> newEntityChanges = new HashMap<>();
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
					operatorMutations = _operatorMutations;
					_operatorMutations = null;
					
					// We need to do some scheduling work under this lock.
					for (Map.Entry<Integer, PerEntitySharedAccess> entry : _entitySharedAccess.entrySet())
					{
						int id = entry.getKey();
						PerEntitySharedAccess access = entry.getValue();
						if (!access.newChanges.isEmpty())
						{
							_EntityMutationWrapper next = access.newChanges.remove();
							newEntityChanges.put(id, next.mutation);
							newCommitLevels.put(id, next.commitLevel);
						}
						else
						{
							// There may not be a previous commit level if this was just added.
							long commitLevel = combinedCommitLevels.containsKey(id)
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
				
				// We now update our mutable collections for the materials to use in the next tick.
				Map<CuboidAddress, CuboidHeightMap> nextTickMutableHeightMaps = new HashMap<>(completedCuboidHeightMap);
				nextTickMutableHeightMaps.putAll(mergedChangedHeightMaps);
				Map<CuboidAddress, List<ScheduledMutation>> pendingMutations = new HashMap<>();
				Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutations = new HashMap<>();
				Map<Integer, List<ScheduledChange>> nextTickChanges = new HashMap<>();
				
				// Add in anything new.
				Set<CuboidAddress> cuboidsLoadedThisTick = new HashSet<>();
				if (null != newCuboids)
				{
					for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
					{
						IReadOnlyCuboidData cuboid = suspended.cuboid();
						CuboidAddress address = cuboid.getCuboidAddress();
						Object old = mutableWorldState.put(address, cuboid);
						// This must not already be present.
						Assert.assertTrue(null == old);
						old = nextTickMutableHeightMaps.put(address, suspended.heightMap());
						Assert.assertTrue(null == old);
						
						// Load any creatures associated with this cuboid.
						for (CreatureEntity loadedCreature : suspended.creatures())
						{
							// We initialize the creature's despawn keep-alive tick to now.
							CreatureEntity creature = loadedCreature.updateKeepAliveTick(_snapshot.tickNumber);
							mutableCreatureState.put(creature.id(), creature);
						}
						
						// Add any suspended mutations which came with the cuboid.
						List<ScheduledMutation> pending = suspended.pendingMutations();
						if (!pending.isEmpty())
						{
							old = pendingMutations.put(address, new ArrayList<>(pending));
							// This must not already be present (this was just created above here).
							Assert.assertTrue(null == old);
						}
						
						// Add any periodic mutations loaded with the cuboid.
						Map<BlockAddress, Long> periodic = suspended.periodicMutationMillis();
						if (!periodic.isEmpty())
						{
							old = periodicMutations.put(address, new HashMap<>(periodic));
							// This must not already be present (this was just created above here).
							Assert.assertTrue(null == old);
						}
						cuboidsLoadedThisTick.add(address);
					}
				}
				if (null != newEntities)
				{
					for (SuspendedEntity suspended : newEntities)
					{
						Entity entity = suspended.entity();
						int id = entity.id();
						Object old = mutableCrowdState.put(id, entity);
						// This must not already be present.
						Assert.assertTrue(null == old);
						
						// Add any suspended mutations which came with the entity.
						List<ScheduledChange> changes = suspended.changes();
						if (!changes.isEmpty())
						{
							old = nextTickChanges.put(id, new ArrayList<>(changes));
							// This must not already be present (this was just created above here).
							Assert.assertTrue(null == old);
						}
					}
				}
				
				// Add any operator mutations.
				if (null != operatorMutations)
				{
					for (_OperatorMutationWrapper wrapper : operatorMutations)
					{
						// The operator mutations must be run against a connected entity or OPERATOR_ENTITY_ID.
						if ((CrowdProcessor.OPERATOR_ENTITY_ID == wrapper.entityId) || mutableCrowdState.containsKey(wrapper.entityId))
						{
							List<ScheduledChange> mutableChanges = nextTickChanges.get(wrapper.entityId);
							if (null == mutableChanges)
							{
								mutableChanges = new ArrayList<>();
								nextTickChanges.put(wrapper.entityId, mutableChanges);
							}
							mutableChanges.add(new ScheduledChange(wrapper.mutation, 0L));
						}
					}
				}
				
				// Add any of the mutations scheduled from the last tick.
				for (List<ScheduledMutation> list : snapshotBlockMutations.values())
				{
					for (ScheduledMutation mutation : list)
					{
						_scheduleMutationForCuboid(pendingMutations, mutation);
					}
				}
				snapshotBlockMutations = null;
				// Also add in any periodic mutations.
				periodicMutations.putAll(snapshotPeriodicMutations);
				snapshotPeriodicMutations = null;
				
				// Remove anything old.
				if (null != cuboidsToDrop)
				{
					for (CuboidAddress address : cuboidsToDrop)
					{
						Object old = mutableWorldState.remove(address);
						// This must already be present.
						Assert.assertTrue(null != old);
						old = nextTickMutableHeightMaps.remove(address);
						Assert.assertTrue(null != old);
						
						// Remove any creatures in this cuboid.
						// TODO:  Change this to use some sort of spatial look-up mechanism since this loop is attrocious.
						Iterator<Map.Entry<Integer, CreatureEntity>> expensive = mutableCreatureState.entrySet().iterator();
						while (expensive.hasNext())
						{
							Map.Entry<Integer, CreatureEntity> one = expensive.next();
							EntityLocation loc = one.getValue().location();
							if (loc.getBlockLocation().getCuboidAddress().equals(address))
							{
								expensive.remove();
							}
						}
						
						// Remove any of the scheduled operations for this cuboid.
						pendingMutations.remove(address);
						periodicMutations.remove(address);
					}
				}
				if (null != removedEntityIds)
				{
					for (int entityId : removedEntityIds)
					{
						Entity old = mutableCrowdState.remove(entityId);
						// This must have been present.
						Assert.assertTrue(null != old);
					}
				}
				for (Map.Entry<Integer, List<ScheduledChange>> entry : snapshotEntityMutations.entrySet())
				{
					// We can't modify the original so use a new container.
					int id = entry.getKey();
					_scheduleChangesForEntity(nextTickChanges, id, new LinkedList<>(entry.getValue()));
				}
				snapshotEntityMutations = null;
				for (Map.Entry<Integer, EntityChangeTopLevelMovement<IMutablePlayerEntity>> container : newEntityChanges.entrySet())
				{
					// These are coming in from outside, so they should be run immediately (no delay for future), after anything already scheduled from the previous tick.
					ScheduledChange change = new ScheduledChange(container.getValue(), 0L);
					List<ScheduledChange> mutableQueue = new LinkedList<>();
					mutableQueue.add(change);
					_scheduleChangesForEntity(nextTickChanges, container.getKey(), mutableQueue);
				}
				newEntityChanges = null;
				
				// TODO:  We should probably remove this once we are sure we know what is happening and/or find a cheaper way to check this.
				for (CuboidAddress key : pendingMutations.keySet())
				{
					if (!mutableWorldState.containsKey(key))
					{
						System.out.println("WARNING: missing cuboid " + key);
					}
				}
				for (CuboidAddress key : periodicMutations.keySet())
				{
					// Given that these can only be scheduled against loaded cuboids, which can only be explicitly unloaded above, anything remaining must still be present.
					Assert.assertTrue(mutableWorldState.containsKey(key));
				}
				for (Integer id : nextTickChanges.keySet())
				{
					if ((CrowdProcessor.OPERATOR_ENTITY_ID != id) && !mutableCrowdState.containsKey(id))
					{
						System.out.println("WARNING: missing entity " + id);
					}
				}
				
				// We want to build the arrangement of blocks modified in the last tick so that block updates can be synthesized.
				Map<CuboidAddress, List<AbsoluteLocation>> updatedBlockLocationsByCuboid = new HashMap<>();
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = new HashMap<>();
				Set<AbsoluteLocation> potentialLogicChangeSet = new HashSet<>();
				for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> entry : blockChangesByCuboid.entrySet())
				{
					List<BlockChangeDescription> list = entry.getValue();
					
					// Only store the updated block locations if the block change requires it.
					List<AbsoluteLocation> locations = list.stream()
						.filter((BlockChangeDescription description) -> description.requiresUpdateEvent())
						.map(
							(BlockChangeDescription update) -> update.serializedForm().getAbsoluteLocation()
						).toList();
					if (!locations.isEmpty())
					{
						updatedBlockLocationsByCuboid.put(entry.getKey(), locations);
					}
					
					// Store the possible lighting update locations, much in the same style.
					List<AbsoluteLocation> lightChangeLocations = list.stream()
							.filter((BlockChangeDescription description) -> description.requiresLightingCheck())
							.map(
								(BlockChangeDescription update) -> update.serializedForm().getAbsoluteLocation()
							).toList();
					if (!lightChangeLocations.isEmpty())
					{
						potentialLightChangesByCuboid.put(entry.getKey(), lightChangeLocations);
					}
					
					// Logic changes are more complicated, as they don't usually change within the block, but adjacent
					// ones (except for conduit changes) so build the set and then split it by cuboid in a later pass.
					for (BlockChangeDescription change : list)
					{
						byte logicBits = change.logicCheckBits();
						if (0x0 != logicBits)
						{
							AbsoluteLocation location = change.serializedForm().getAbsoluteLocation();
							LogicLayerHelpers.populateSetWithPotentialLogicChanges(potentialLogicChangeSet, location, logicBits);
						}
					}
				}
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid = new HashMap<>();
				for (AbsoluteLocation location : potentialLogicChangeSet)
				{
					CuboidAddress cuboid = location.getCuboidAddress();
					if (!potentialLogicChangesByCuboid.containsKey(cuboid))
					{
						potentialLogicChangesByCuboid.put(cuboid, new ArrayList<>());
					}
					potentialLogicChangesByCuboid.get(cuboid).add(location);
				}
				
				// We now have a plan for this tick so save it in the ivar so the other threads can grab it.
				long endMillisPreamble = System.currentTimeMillis();
				long millisInNextTickPreamble = endMillisPreamble - startMillisPreamble;
				
				// WARNING:  completedHeightMaps does NOT include the new height maps loaded after the previous tick finished!
				// (this is done to avoid the cost of rebuilding the maps since the column height maps are not guaranteed to be fully accurate)
				_thisTickMaterials = new TickMaterials(_nextTick
						, Collections.unmodifiableMap(mutableWorldState)
						, Collections.unmodifiableMap(nextTickMutableHeightMaps)
						, completedHeightMaps
						, Collections.unmodifiableMap(mutableCrowdState)
						// completedCreatures
						, Collections.unmodifiableMap(mutableCreatureState)
						
						, pendingMutations
						, periodicMutations
						, nextTickChanges
						// creatureChanges
						, nextCreatureChanges
						, updatedBlockLocationsByCuboid
						, potentialLightChangesByCuboid
						, potentialLogicChangesByCuboid
						, cuboidsLoadedThisTick
						
						// Data only used by this method:
						, newCommitLevels
						
						// Store the partial tick stats.
						, millisInNextTickPreamble
						, endMillisPreamble
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

	private void _scheduleMutationForCuboid(Map<CuboidAddress, List<ScheduledMutation>> nextTickMutations, ScheduledMutation mutation)
	{
		CuboidAddress address = mutation.mutation().getAbsoluteLocation().getCuboidAddress();
		List<ScheduledMutation> queue = nextTickMutations.get(address);
		if (null == queue)
		{
			queue = new LinkedList<>();
			nextTickMutations.put(address, queue);
		}
		queue.add(mutation);
	}

	private <T> void _scheduleChangesForEntity(Map<Integer, List<T>> nextTickChanges, int entityId, List<T> changes)
	{
		List<T> queue = nextTickChanges.get(entityId);
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


	/**
	 * The snapshot of immutable state created whenever a tick is completed.
	 */
	public static record Snapshot(long tickNumber
			, Map<CuboidAddress, SnapshotCuboid> cuboids
			, Map<Integer, SnapshotEntity> entities
			, Map<Integer, SnapshotCreature> creatures
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			
			, List<EventRecord> postedEvents
			
			, TickStats stats
	)
	{}

	public static record SnapshotCuboid(
			// Never null.
			IReadOnlyCuboidData completed
			// Null if there are no changes or non-empty.
			, List<MutationBlockSetBlock> blockChanges
			// Never null but can be empty.
			, List<ScheduledMutation> scheduledBlockMutations
			// Never null but can be empty.
			, Map<BlockAddress, Long> periodicMutationMillis
	)
	{}

	public static record SnapshotEntity(
			// Never null.
			Entity completed
			// The last commit level from the connected client.
			, long commitLevel
			// Null if not changed in this tick.
			, Entity updated
			// Never null but can be empty.
			, List<ScheduledChange> scheduledMutations
	)
	{}

	public static record SnapshotCreature(
			// Never null.
			CreatureEntity completed
			// Null if not visibly changed in this tick.
			, CreatureEntity visiblyChanged
	)
	{}

	public static record TickStats(long tickNumber
			, long millisTickPreamble
			, long millisTickParallelPhase
			, long millisTickPostamble
			, ProcessorElement.PerThreadStats[] threadStats
			, int committedEntityMutationCount
			, int committedCuboidMutationCount
	) {
		public void writeToStream(PrintStream out)
		{
			long preamble = this.millisTickPreamble;
			long parallel = this.millisTickParallelPhase;
			long postamble = this.millisTickPostamble;
			long tickTime = preamble + parallel + postamble;
			out.println("Log for slow (" + tickTime + " ms) tick " + this.tickNumber);
			out.println("\tPreamble: " + preamble + " ms");
			out.println("\tParallel: " + parallel + " ms");
			for (ProcessorElement.PerThreadStats thread : this.threadStats)
			{
				out.println("\t-Crowd: " + thread.millisInCrowdProcessor() + " ms, Creatures: " + thread.millisInCreatureProcessor() + " ms, World: " + thread.millisInWorldProcessor() + " ms");
				out.println("\t\tEntities processed: " + thread.entitiesProcessed() + ", changes processed " + thread.entityChangesProcessed());
				out.println("\t\tCreatures processed: " + thread.creaturesProcessed() + ", changes processed " + thread.creatureChangesProcessed());
				out.println("\t\tCuboids processed: " + thread.cuboidsProcessed() + ", mutations processed " + thread.cuboidMutationsProcessed() + ", updates processed " + thread.cuboidBlockupdatesProcessed());
			}
			out.println("\tPostamble: " + postamble + " ms");
		}
	}

	private static record TickMaterials(long thisGameTick
			// Read-only versions of the cuboids produced by the previous tick (by address).
			, Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
			, Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			// Read-only versions of the Entities produced by the previous tick (by ID).
			, Map<Integer, Entity> completedEntities
			// Read-only versions of the creatures from the previous tick (by ID).
			, Map<Integer, CreatureEntity> completedCreatures
			// The block mutations to run in this tick (by cuboid address).
			, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
			// We handle "periodic" mutations differently since they saturate for a given location.
			, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationMillis
			// The entity mutations to run in this tick (by ID).
			, Map<Integer, List<ScheduledChange>> changesToRun
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges
			// The blocks modified in the last tick, represented as a list per cuboid where they originate.
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			// The blocks which were modified in such a way that they may require a lighting update.
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			// The blocks which were modified in such a way that they may have changed the logic aspect which needs to
			// be propagated.
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
			// The set of addresses loaded in this tick (they are present in this tick, but for the first time).
			, Set<CuboidAddress> cuboidsLoadedThisTick
			
			// ----- TickRunner private state attached to the materials below this line -----
			// The last commit levels of all connected clients (by ID).
			, Map<Integer, Long> commitLevels
			
			// Data related to internal statistics to be passed back at the end of the tick.
			, long millisInTickPreamble
			, long timeMillisPreambleEnd
	) {}

	/**
	 * The per-entity data shared between foreground and background threads for scheduling changes.
	 * 
	 * This is now reduced to purely being a queue of pending changes, the type only remaining as a wrapper to make this
	 * use-case clearer in the code.
	 * Note that they queue is long-lived and mutated under the shared lock.
	 */
	private static class PerEntitySharedAccess
	{
		private final Queue<_EntityMutationWrapper> newChanges = new LinkedList<>();
	}

	/**
	 * A wrapper over the IMutationEntity to store commit level data.
	 */
	private static record _EntityMutationWrapper(EntityChangeTopLevelMovement<IMutablePlayerEntity> mutation, long commitLevel) {}

	/**
	 * A wrapper over the IMutationEntity with associated entity ID.
	 */
	private static record _OperatorMutationWrapper(int entityId, IEntityAction<IMutablePlayerEntity> mutation) {}

	/**
	 * A wrapper over the per-thread partial data which we hand-off at synchronization.
	 */
	private static record _PartialHandoffData(WorldProcessor.ProcessedFragment world
			, CrowdProcessor.ProcessedGroup crowd
			, CreatureProcessor.CreatureGroup creatures
			, List<CreatureEntity> spawnedCreatures
			, List<ScheduledMutation> newlyScheduledMutations
			, Map<Integer, List<ScheduledChange>> newlyScheduledChanges
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges
			, List<EventRecord> postedEvents
			, Map<Integer, Long> commitLevels
	) {}
}
