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
import java.util.stream.Collectors;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.engine.EngineCreatures;
import com.jeffdisher.october.engine.EngineSpawner;
import com.jeffdisher.october.engine.EnginePlayers;
import com.jeffdisher.october.engine.EngineCuboids;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
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
import com.jeffdisher.october.types.IEntityAction;
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
	private final Consumer<Snapshot> _tickCompletionListener;
	private final WorldConfig _config;

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
		_tickCompletionListener = tickCompletionListener;
		_config = config;
		
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
					_backgroundThreadMain(thisThread);
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
				// internallyMarkedAlive
				, Set.of()
				
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
	public boolean enqueueEntityChange(int entityId, EntityActionSimpleMove<IMutablePlayerEntity> change, long commitLevel)
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
		Assert.assertTrue((entityId > 0) || (EnginePlayers.OPERATOR_ENTITY_ID == entityId));
		
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


	private void _backgroundThreadMain(ProcessorElement thisThread)
	{
		// There is nothing loaded at the start so pass in an empty world and crowd state, as well as no work having been processed.
		TickMaterials emptyMaterials = new TickMaterials(0L
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, List.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			
			, Map.of()
			
			, EntityCollection.emptyCollection()
			
			, 0L
			, System.currentTimeMillis()
		);
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, emptyMaterials
				, new _PartialHandoffData(new _ProcessedFragment(Map.of(), Map.of(), List.of(), Map.of(), Map.of(), 0)
						, new _ProcessedGroup(0, Map.of())
						, new _CreatureGroup(false, Map.of(), List.of())
						, List.of()
						, List.of()
						, Map.of()
						, Map.of()
						, List.of()
						, Set.of()
				)
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
			Set<CuboidAddress> internallyMarkedAlive = new HashSet<>();
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
									? PropagationHelpers.currentSkyLightValue(thisTickMaterials.thisGameTick, _config.ticksPerDay, _config.dayStartTick)
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
					, (CuboidAddress address) -> internallyMarkedAlive.add(address)
					, _config
					, _millisPerTick
					, currentTickTimeMillis
			);
			
			// We will have the first thread attempt the monster spawning algorithm.
			if (thisThread.handleNextWorkUnit())
			{
				// This will spawn in the context, if spawning is appropriate.
				EngineSpawner.trySpawnCreature(context
						, materials.entityCollection
						, materials.completedCuboids
						, materials.completedHeightMaps
						, materials.completedCreatures
				);
			}
			
			long startCreatures = System.currentTimeMillis();
			_CreatureGroup creatureGroup = _processCreatureGroupParallel(thisThread
					, materials.completedCreatures
					, context
					, materials.entityCollection
					, materials.creatureChanges
			);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != creatureGroup);
			
			long startCrowd = System.currentTimeMillis();
			_ProcessedGroup group = _processCrowdGroupParallel(thisThread
					, context
					, materials.entityCollection
					, materials.changesToRun
					, materials.operatorChanges
			);
			// There is always a returned group (even if it has no content).
			Assert.assertTrue(null != group);
			// Now, process the world changes.
			long startWorld = System.currentTimeMillis();
			_ProcessedFragment fragment = _processWorldFragmentParallel(thisThread
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
					, materials
					, new _PartialHandoffData(fragment
							, group
							, creatureGroup
							, spawnedCreatures
							, newMutationSink.takeExportedMutations()
							, newChangeSink.takeExportedChanges()
							, newChangeSink.takeExportedCreatureChanges()
							, events
							, internallyMarkedAlive
					)
			);
		}
	}

	private static _CreatureGroup _processCreatureGroupParallel(ProcessorElement processor
			, Map<Integer, CreatureEntity> creaturesById
			, TickProcessingContext context
			, EntityCollection entityCollection
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun
	)
	{
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		for (Map.Entry<Integer, CreatureEntity> elt : creaturesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				CreatureEntity creature = elt.getValue();
				List<IEntityAction<IMutableCreatureEntity>> changes = changesToRun.get(id);
				processor.creaturesProcessed += 1;
				if (null != changes)
				{
					processor.creatureChangesProcessed += changes.size();
				}
				EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context, entityCollection, creature, changes);
				if (null == result.updatedEntity())
				{
					deadCreatureIds.add(id);
				}
				else if (result.updatedEntity() != creature)
				{
					updatedCreatures.put(id, result.updatedEntity());
				}
				if (!result.didTakeSpecialAction())
				{
					processor.creatureChangesProcessed += 1;
				}
			}
		}
		return new _CreatureGroup(false
				, updatedCreatures
				, deadCreatureIds
		);
	}

	private static _ProcessedGroup _processCrowdGroupParallel(ProcessorElement processor
			, TickProcessingContext context
			, EntityCollection entityCollection
			, Map<Integer, _InputEntity> entitiesById
			, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
	)
	{
		Map<Integer, _OutputEntity> processedEntities = new HashMap<>();
		int committedMutationCount = 0;
		
		// We need to check the operator as a special-case since it isn't a real entity.
		if (processor.handleNextWorkUnit())
		{
			// Verify that this isn't redundantly described.
			Assert.assertTrue(!entitiesById.containsKey(EnginePlayers.OPERATOR_ENTITY_ID));
			EnginePlayers.processOperatorActions(context, operatorChanges);
		}
		for (Map.Entry<Integer, _InputEntity> elt : entitiesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				_InputEntity input = elt.getValue();
				Entity entity = input.entity();
				List<ScheduledChange> changes = input.scheduledChanges();
				processor.entitiesProcessed += 1;
				
				EnginePlayers.SinglePlayerResult result = EnginePlayers.processOnePlayer(context
					, entityCollection
					, entity
					, changes
				);
				processedEntities.put(id, new _OutputEntity(result.changedEntityOrNull(), result.notYetReadyChanges()));
				processor.entityChangesProcessed = +result.entityChangesProcessed();
				committedMutationCount += result.committedMutationCount();
			}
		}
		return new _ProcessedGroup(committedMutationCount
			, processedEntities
		);
	}

	private static _ProcessedFragment _processWorldFragmentParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, TickProcessingContext context
			, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
			, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationMillis
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> fragmentHeights = new HashMap<>();
		
		// We need to walk all the loaded cuboids, just to make sure that there were no updates.
		Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicNotReadyByCuboid = new HashMap<>();
		int committedMutationCount = 0;
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				processor.cuboidsProcessed += 1;
				CuboidAddress key = elt.getKey();
				IReadOnlyCuboidData oldState = elt.getValue();
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != oldState);
				EngineCuboids.SingleCuboidResult result = EngineCuboids.processOneCuboid(context
					, worldMap.keySet()
					, mutationsToRun.get(key)
					, periodicMutationMillis.get(key)
					, modifiedBlocksByCuboidAddress
					, potentialLightChangesByCuboid
					, potentialLogicChangesByCuboid
					, cuboidsLoadedThisTick
					, key
					, oldState
				);
				if (null != result.changedCuboidOrNull())
				{
					fragment.put(key, result.changedCuboidOrNull());
				}
				if (null != result.changedHeightMap())
				{
					fragmentHeights.put(key, result.changedHeightMap());
				}
				if (null != result.changedBlocks())
				{
					Assert.assertTrue(!result.changedBlocks().isEmpty());
					blockChangesByCuboid.put(key, result.changedBlocks());
				}
				if (null != result.notYetReadyMutations())
				{
					notYetReadyMutations.addAll(result.notYetReadyMutations());
				}
				if (null != result.periodicNotReady())
				{
					Assert.assertTrue(!result.periodicNotReady().isEmpty());
					periodicNotReadyByCuboid.put(key, result.periodicNotReady());
				}
				processor.cuboidBlockupdatesProcessed += result.blockUpdatesProcessed();
				processor.cuboidMutationsProcessed += result.mutationsProcessed();
				committedMutationCount += result.blockUpdatesApplied() + result.mutationsApplied();
			}
		}
		
		// We package up any of the work that we did (note that no thread will return a cuboid which had no mutations in its fragment).
		return new _ProcessedFragment(fragment
				, fragmentHeights
				, notYetReadyMutations
				, periodicNotReadyByCuboid
				, blockChangesByCuboid
				, committedMutationCount
		);
	}

	private TickMaterials _mergeTickStateAndWaitForNext(ProcessorElement elt
			, TickMaterials startingMaterials
			, _PartialHandoffData perThreadData
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
			
			// We will merge together all the per-thread fragments into one master fragment.
			_PartialHandoffData masterFragment = _mergeAndClearPartialFragments(_partial);
			
			Map<Integer, Entity> mutableCrowdState = _extractSnapshotPlayers(startingMaterials, masterFragment);
			Map<Integer, CreatureEntity> mutableCreatureState = _extractSnapshotCreatures(startingMaterials, masterFragment);
			
			Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState = new HashMap<>(startingMaterials.completedCuboids);
			mutableWorldState.putAll(masterFragment.world.stateFragment());
			Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.rebuildColumnMaps(startingMaterials.completedHeightMaps
				, startingMaterials.cuboidHeightMaps
				, masterFragment.world.heightFragment()
				, mutableWorldState.keySet()
			);
			
			Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = _extractSnapshotBlockMutations(masterFragment);
			Map<Integer, List<ScheduledChange>> snapshotEntityMutations = _extractPlayerEntityChanges(masterFragment);
			Map<Integer, Entity> updatedEntities = _extractChangedPlayerEntitiesOnly(masterFragment);
			
			// Collect the time stamps for stats.
			long endMillisPostamble = System.currentTimeMillis();
			long millisTickParallelPhase = (startMillisPostamble - startingMaterials.timeMillisPreambleEnd);
			long millisTickPostamble = (endMillisPostamble - startMillisPostamble);
			
			// ***************** Tick ends here *********************
			
			// At this point, the tick to advance the world and crowd states has completed so publish the read-only results and wait before we put together the materials for the next tick.
			// Acknowledge that the tick is completed by creating a snapshot of the state.
			Snapshot completedTick = _buildSnapshot(_nextTick
				, startingMaterials
				, masterFragment
				, mutableWorldState
				, mutableCrowdState
				, mutableCreatureState
				, completedHeightMaps
				, snapshotBlockMutations
				, snapshotEntityMutations
				, updatedEntities
				, _threadStats.clone()
				, millisTickParallelPhase
				, millisTickPostamble
			);
			
			// We want to pass this to a listener before we synchronize to avoid calling out under monitor.
			_tickCompletionListener.accept(completedTick);
			
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
				Map<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> newEntityChanges = new HashMap<>();
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
							long commitLevel = startingMaterials.commitLevels.containsKey(id)
									? startingMaterials.commitLevels.get(id)
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
				Map<CuboidAddress, CuboidHeightMap> nextTickMutableHeightMaps = new HashMap<>(startingMaterials.cuboidHeightMaps);
				nextTickMutableHeightMaps.putAll(masterFragment.world.heightFragment());
				_poopulateWorldWithNewCuboids(mutableWorldState, nextTickMutableHeightMaps, newCuboids);
				_addCreaturesInNewCuboids(mutableCreatureState, newCuboids, _snapshot.tickNumber);
				Map<CuboidAddress, List<ScheduledMutation>> pendingMutations = _extractPendingMutationsFromNewCuboids(newCuboids, snapshotBlockMutations);
				Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutations = _extractPeriodicMutationsFromNewCuboids(newCuboids, masterFragment);
				
				// Add in anything new.
				Set<CuboidAddress> cuboidsLoadedThisTick = (null != newCuboids)
					? newCuboids.stream()
						.map((SuspendedCuboid<IReadOnlyCuboidData> suspended) -> suspended.cuboid().getCuboidAddress())
						.collect(Collectors.toSet())
					: Set.of()
				;
				_populateWithNewPlayers(mutableCrowdState, newEntities);
				Map<Integer, List<ScheduledChange>> nextTickChanges = _combineEntityActions(snapshotEntityMutations, newEntities, newEntityChanges, operatorMutations);
				
				// Add any operator actions (in this path, we only select the actions for the operator entity).
				List<IEntityAction<IMutablePlayerEntity>> operatorChanges = (null != operatorMutations)
					? operatorMutations.stream()
						.filter((_OperatorMutationWrapper wrapper) -> (EnginePlayers.OPERATOR_ENTITY_ID == wrapper.entityId))
						.map((_OperatorMutationWrapper wrapper) -> wrapper.mutation)
						.toList()
					: List.of()
				;
				
				// We can also extract any creature changes scheduled in the previous tick (creature actions are not saved in the cuboid so we only have what was scheduled in previous tick).
				Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> nextCreatureChanges = _scheduleNewCreatureActions(masterFragment);
				
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
				
				// Convert this raw next tick action accumulation into the CrowdProcessor input.
				Map<Integer, _InputEntity> changesToRun = _determineChangesToRun(mutableCrowdState, nextTickChanges);
				
				// We want to build the arrangement of blocks modified in the last tick so that block updates can be synthesized.
				Map<CuboidAddress, List<AbsoluteLocation>> updatedBlockLocationsByCuboid = _extractBlockUpdateLocations( masterFragment);
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = _extractPotentialLightChanges( masterFragment);
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid = _extractPotentialLogicChanges( masterFragment);
				
				// Collect the last timing data for this tick preamble.
				long endMillisPreamble = System.currentTimeMillis();
				long millisInNextTickPreamble = endMillisPreamble - startMillisPreamble;
				
				// WARNING:  completedHeightMaps does NOT include the new height maps loaded after the previous tick finished!
				// (this is done to avoid the cost of rebuilding the maps since the column height maps are not guaranteed to be fully accurate)
				EntityCollection entityCollection = EntityCollection.fromMaps(mutableCrowdState, mutableCreatureState);
				_thisTickMaterials = new TickMaterials(_nextTick
						, Collections.unmodifiableMap(mutableWorldState)
						, Collections.unmodifiableMap(nextTickMutableHeightMaps)
						, completedHeightMaps
						, Collections.unmodifiableMap(mutableCrowdState)
						// completedCreatures
						, Collections.unmodifiableMap(mutableCreatureState)
						
						, pendingMutations
						, periodicMutations
						, Collections.unmodifiableMap(changesToRun)
						, operatorChanges
						// creatureChanges
						, nextCreatureChanges
						, updatedBlockLocationsByCuboid
						, potentialLightChangesByCuboid
						, potentialLogicChangesByCuboid
						, cuboidsLoadedThisTick
						
						// Data only used by this method:
						, newCommitLevels
						
						, entityCollection
						
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

	private static Map<Integer, Entity> _extractSnapshotPlayers(TickMaterials startingMaterials, _PartialHandoffData masterFragment)
	{
		Map<Integer, Entity> mutableCrowdState = new HashMap<>(startingMaterials.completedEntities);
		for (Map.Entry<Integer, _OutputEntity> processed : masterFragment.crowd.entityOutput().entrySet())
		{
			Integer key = processed.getKey();
			_OutputEntity value = processed.getValue();
			Entity updated = value.entity();
			
			// Note that this is documented to be null if nothing changed.
			if (null != updated)
			{
				mutableCrowdState.put(key, updated);
			}
		}
		return mutableCrowdState;
	}

	private static Map<CuboidAddress, List<ScheduledMutation>> _extractSnapshotBlockMutations(_PartialHandoffData masterFragment)
	{
		Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = new HashMap<>();
		for (ScheduledMutation scheduledMutation : masterFragment.newlyScheduledMutations)
		{
			_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
		}
		for (ScheduledMutation scheduledMutation : masterFragment.world.notYetReadyMutations())
		{
			_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
		}
		return snapshotBlockMutations;
	}

	private static Map<Integer, CreatureEntity> _extractSnapshotCreatures(TickMaterials startingMaterials, _PartialHandoffData masterFragment)
	{
		Map<Integer, CreatureEntity> mutableCreatureState = new HashMap<>(startingMaterials.completedCreatures);
		mutableCreatureState.putAll(masterFragment.creatures.updatedCreatures());
		for (Integer creatureId : masterFragment.creatures.deadCreatureIds())
		{
			mutableCreatureState.remove(creatureId);
		}
		for (CreatureEntity newCreature : masterFragment.spawnedCreatures())
		{
			mutableCreatureState.put(newCreature.id(), newCreature);
		}
		return mutableCreatureState;
	}

	private static Map<Integer, List<ScheduledChange>> _extractPlayerEntityChanges(_PartialHandoffData masterFragment)
	{
		Map<Integer, List<ScheduledChange>> snapshotEntityMutations = new HashMap<>();
		for (Map.Entry<Integer, List<ScheduledChange>> container : masterFragment.newlyScheduledChanges().entrySet())
		{
			_scheduleChangesForEntity(snapshotEntityMutations, container.getKey(), container.getValue());
		}
		for (Map.Entry<Integer, _OutputEntity> processed : masterFragment.crowd.entityOutput().entrySet())
		{
			Integer key = processed.getKey();
			_OutputEntity value = processed.getValue();
			
			// We want to schedule anything which wasn't yet ready.
			List<ScheduledChange> notYetReadyChanges = value.notYetReadyChanges();
			if (!notYetReadyChanges.isEmpty())
			{
				_scheduleChangesForEntity(snapshotEntityMutations, key, notYetReadyChanges);
			}
		}
		return snapshotEntityMutations;
	}

	private static Map<Integer, Entity> _extractChangedPlayerEntitiesOnly(_PartialHandoffData masterFragment)
	{
		Map<Integer, Entity> updatedEntities = new HashMap<>();
		for (Map.Entry<Integer, _OutputEntity> processed : masterFragment.crowd.entityOutput().entrySet())
		{
			Integer key = processed.getKey();
			_OutputEntity value = processed.getValue();
			Entity updated = value.entity();
			
			// Note that this is documented to be null if nothing changed.
			if (null != updated)
			{
				Entity old = updatedEntities.put(key, updated);
				Assert.assertTrue(null == old);
			}
		}
		return updatedEntities;
	}

	private static Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> _scheduleNewCreatureActions(_PartialHandoffData masterFragment)
	{
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> nextCreatureChanges = new HashMap<>();
		for (Map.Entry<Integer, List<IEntityAction<IMutableCreatureEntity>>> container : masterFragment.newlyScheduledCreatureChanges().entrySet())
		{
			_scheduleChangesForEntity(nextCreatureChanges, container.getKey(), container.getValue());
		}
		return nextCreatureChanges;
	}

	// NOTE:  Modifies out_mutableWorldState and out_nextTickMutableHeightMaps.
	private static void _poopulateWorldWithNewCuboids(Map<CuboidAddress, IReadOnlyCuboidData> out_mutableWorldState
		, Map<CuboidAddress, CuboidHeightMap> out_nextTickMutableHeightMaps
		, List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids
	)
	{
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				IReadOnlyCuboidData cuboid = suspended.cuboid();
				CuboidAddress address = cuboid.getCuboidAddress();
				Object old = out_mutableWorldState.put(address, cuboid);
				// This must not already be present.
				Assert.assertTrue(null == old);
				old = out_nextTickMutableHeightMaps.put(address, suspended.heightMap());
				Assert.assertTrue(null == old);
			}
		}
	}

	// NOTE:  Modifies out_mutableCreatureState.
	private static void _addCreaturesInNewCuboids(Map<Integer, CreatureEntity> out_mutableCreatureState, List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids, long tickNumber)
	{
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				// Load any creatures associated with this cuboid.
				for (CreatureEntity loadedCreature : suspended.creatures())
				{
					// We initialize the creature's despawn keep-alive tick to now.
					CreatureEntity creature = loadedCreature.updateKeepAliveTick(tickNumber);
					out_mutableCreatureState.put(creature.id(), creature);
				}
			}
		}
	}

	private static Map<CuboidAddress, List<ScheduledMutation>> _extractPendingMutationsFromNewCuboids(List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids, Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations)
	{
		Map<CuboidAddress, List<ScheduledMutation>> pendingMutations = new HashMap<>();
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				IReadOnlyCuboidData cuboid = suspended.cuboid();
				CuboidAddress address = cuboid.getCuboidAddress();
				
				// Add any suspended mutations which came with the cuboid.
				List<ScheduledMutation> pending = suspended.pendingMutations();
				if (!pending.isEmpty())
				{
					Object old = pendingMutations.put(address, new ArrayList<>(pending));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
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
		return pendingMutations;
	}

	private static Map<CuboidAddress, Map<BlockAddress, Long>> _extractPeriodicMutationsFromNewCuboids(List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids, _PartialHandoffData masterFragment)
	{
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutations = new HashMap<>();
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				IReadOnlyCuboidData cuboid = suspended.cuboid();
				CuboidAddress address = cuboid.getCuboidAddress();
				
				// Add any periodic mutations loaded with the cuboid.
				Map<BlockAddress, Long> periodic = suspended.periodicMutationMillis();
				if (!periodic.isEmpty())
				{
					Object old = periodicMutations.put(address, new HashMap<>(periodic));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
				}
			}
		}
		// Also add in any periodic mutations.
		periodicMutations.putAll(masterFragment.world.periodicNotReadyByCuboid());
		return periodicMutations;
	}

	// NOTE:  Modifies out_mutableCrowdState.
	private static void _populateWithNewPlayers(Map<Integer, Entity> out_mutableCrowdState
		, List<SuspendedEntity> newEntities
	)
	{
		if (null != newEntities)
		{
			for (SuspendedEntity suspended : newEntities)
			{
				Entity entity = suspended.entity();
				int id = entity.id();
				Object old = out_mutableCrowdState.put(id, entity);
				// This must not already be present.
				Assert.assertTrue(null == old);
			}
		}
	}

	private static Map<Integer, List<ScheduledChange>> _combineEntityActions(Map<Integer, List<ScheduledChange>> snapshotEntityMutations
		, List<SuspendedEntity> newEntities
		, Map<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> newEntityChanges
		, List<_OperatorMutationWrapper> operatorMutations
	)
	{
		Map<Integer, List<ScheduledChange>> nextTickChanges = new HashMap<>();
		if (null != newEntities)
		{
			for (SuspendedEntity suspended : newEntities)
			{
				Entity entity = suspended.entity();
				int id = entity.id();
				
				// Add any suspended mutations which came with the entity.
				List<ScheduledChange> changes = suspended.changes();
				if (!changes.isEmpty())
				{
					Object old = nextTickChanges.put(id, new ArrayList<>(changes));
					// This must not already be present (this was just created above here).
					Assert.assertTrue(null == old);
				}
			}
		}
		for (Map.Entry<Integer, List<ScheduledChange>> entry : snapshotEntityMutations.entrySet())
		{
			// We can't modify the original so use a new container.
			int id = entry.getKey();
			_scheduleChangesForEntity(nextTickChanges, id, new LinkedList<>(entry.getValue()));
		}
		for (Map.Entry<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> container : newEntityChanges.entrySet())
		{
			// These are coming in from outside, so they should be run immediately (no delay for future), after anything already scheduled from the previous tick.
			ScheduledChange change = new ScheduledChange(container.getValue(), 0L);
			List<ScheduledChange> mutableQueue = new LinkedList<>();
			mutableQueue.add(change);
			_scheduleChangesForEntity(nextTickChanges, container.getKey(), mutableQueue);
		}
		if (null != operatorMutations)
		{
			for (_OperatorMutationWrapper wrapper : operatorMutations)
			{
				// If the operator change isn't targeting the operator entity, schedule it on the specific player entity.
				if (EnginePlayers.OPERATOR_ENTITY_ID != wrapper.entityId)
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
		return nextTickChanges;
	}

	private static Map<Integer, _InputEntity> _determineChangesToRun(Map<Integer, Entity> mutableCrowdState, Map<Integer, List<ScheduledChange>> nextTickChanges)
	{
		Map<Integer, _InputEntity> changesToRun = new HashMap<>();
		// We shouldn't have put operator changes into this common map.
		Assert.assertTrue(!nextTickChanges.containsKey(EnginePlayers.OPERATOR_ENTITY_ID));
		for (Map.Entry<Integer, List<ScheduledChange>> oneEntity : nextTickChanges.entrySet())
		{
			Integer id = oneEntity.getKey();
			Entity entity = mutableCrowdState.get(id);
			if (null != entity)
			{
				List<ScheduledChange> list = oneEntity.getValue();
				// If this is in the map, it can't be empty.
				Assert.assertTrue(!list.isEmpty());
				_InputEntity input = new _InputEntity(entity, Collections.unmodifiableList(list));
				changesToRun.put(id, input);
			}
			else
			{
				System.out.println("WARNING: missing entity " + id);
			}
		}
		return changesToRun;
	}

	private static Map<CuboidAddress, List<AbsoluteLocation>> _extractBlockUpdateLocations(_PartialHandoffData masterFragment)
	{
		Map<CuboidAddress, List<AbsoluteLocation>> updatedBlockLocationsByCuboid = new HashMap<>();
		for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> entry : masterFragment.world.blockChangesByCuboid().entrySet())
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
		}
		return updatedBlockLocationsByCuboid;
	}

	private static Map<CuboidAddress, List<AbsoluteLocation>> _extractPotentialLightChanges(_PartialHandoffData masterFragment)
	{
		Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = new HashMap<>();
		for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> entry : masterFragment.world.blockChangesByCuboid().entrySet())
		{
			List<BlockChangeDescription> list = entry.getValue();
			
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
		}
		return potentialLightChangesByCuboid;
	}

	private static Map<CuboidAddress, List<AbsoluteLocation>> _extractPotentialLogicChanges(_PartialHandoffData masterFragment)
	{
		Set<AbsoluteLocation> potentialLogicChangeSet = new HashSet<>();
		for (List<BlockChangeDescription> list : masterFragment.world.blockChangesByCuboid().values())
		{
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
		return potentialLogicChangesByCuboid;
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

	private static void _scheduleMutationForCuboid(Map<CuboidAddress, List<ScheduledMutation>> nextTickMutations, ScheduledMutation mutation)
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

	private static <T> void _scheduleChangesForEntity(Map<Integer, List<T>> nextTickChanges, int entityId, List<T> changes)
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

	private static _PartialHandoffData _mergeAndClearPartialFragments(_PartialHandoffData[] partials)
	{
		// EngineCuboids.ProcessedFragment world
		Map<CuboidAddress, IReadOnlyCuboidData> stateFragment = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> heightFragment = new HashMap<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicNotReadyByCuboid = new HashMap<>();
		Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
		int world_committedMutationCount = 0;
		
		// EnginePlayers.ProcessedGroup crowd
		int players_committedMutationCount = 0;
		Map<Integer, _OutputEntity> entityOutput = new HashMap<>();
		
		// EngineCreatures.CreatureGroup creatures
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		
		List<CreatureEntity> spawnedCreatures = new ArrayList<>();
		List<ScheduledMutation> newlyScheduledMutations = new ArrayList<>();
		Map<Integer, List<ScheduledChange>> newlyScheduledChanges = new HashMap<>();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges = new HashMap<>();
		List<EventRecord> postedEvents = new ArrayList<>();
		Set<CuboidAddress> internallyMarkedAlive = new HashSet<>();
		
		for (int i = 0; i < partials.length; ++i)
		{
			_PartialHandoffData fragment = partials[i];
			
			// EngineCuboids.ProcessedFragment world
			stateFragment.putAll(fragment.world().stateFragment());
			heightFragment.putAll(fragment.world().heightFragment());
			notYetReadyMutations.addAll(fragment.world().notYetReadyMutations());
			periodicNotReadyByCuboid.putAll(fragment.world().periodicNotReadyByCuboid());
			blockChangesByCuboid.putAll(fragment.world().blockChangesByCuboid());
			world_committedMutationCount += fragment.world().committedMutationCount();
			
			// EnginePlayers.ProcessedGroup crowd
			players_committedMutationCount += fragment.crowd().committedMutationCount();
			entityOutput.putAll(fragment.crowd().entityOutput());
			
			// EngineCreatures.CreatureGroup creatures
			updatedCreatures.putAll(fragment.creatures().updatedCreatures());
			deadCreatureIds.addAll(fragment.creatures().deadCreatureIds());
			
			spawnedCreatures.addAll(fragment.spawnedCreatures());
			newlyScheduledMutations.addAll(fragment.newlyScheduledMutations());
			newlyScheduledChanges.putAll(fragment.newlyScheduledChanges());
			newlyScheduledCreatureChanges.putAll(fragment.newlyScheduledCreatureChanges());
			postedEvents.addAll(fragment.postedEvents());
			internallyMarkedAlive.addAll(fragment.internallyMarkedAlive());
			partials[i] = null;
		}
		
		_ProcessedFragment world = new _ProcessedFragment(stateFragment
			, heightFragment
			, notYetReadyMutations
			, periodicNotReadyByCuboid
			, blockChangesByCuboid
			, world_committedMutationCount
		);
		_ProcessedGroup crowd = new _ProcessedGroup(players_committedMutationCount
			, entityOutput
		);
		_CreatureGroup creatures = new _CreatureGroup(false
			, updatedCreatures
			, deadCreatureIds
		);
		return new _PartialHandoffData(world
			, crowd
			, creatures
			, spawnedCreatures
			, newlyScheduledMutations
			, newlyScheduledChanges
			, newlyScheduledCreatureChanges
			, postedEvents
			, internallyMarkedAlive
		);
	}

	private static Snapshot _buildSnapshot(long tickNumber
		, TickMaterials startingMaterials
		, _PartialHandoffData masterFragment
		, Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState
		, Map<Integer, Entity> mutableCrowdState
		, Map<Integer, CreatureEntity> mutableCreatureState
		, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
		, Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations
		, Map<Integer, List<ScheduledChange>> snapshotEntityMutations
		, Map<Integer, Entity> updatedEntities
		, ProcessorElement.PerThreadStats[] threadStats
		, long millisTickParallelPhase
		, long millisTickPostamble
	)
	{
		Map<CuboidAddress, List<MutationBlockSetBlock>> resultantBlockChangesByCuboid = new HashMap<>();
		for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> perCuboid : masterFragment.world.blockChangesByCuboid().entrySet())
		{
			List<MutationBlockSetBlock> list = perCuboid.getValue().stream()
					.map((BlockChangeDescription description) -> description.serializedForm())
					.toList();
			resultantBlockChangesByCuboid.put(perCuboid.getKey(), list);
		}
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
			Map<BlockAddress, Long> periodicMutationMillis = masterFragment.world.periodicNotReadyByCuboid().get(key);
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
			long commitLevel = startingMaterials.commitLevels.get(key);
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
			CreatureEntity visiblyChanged = masterFragment.creatures.updatedCreatures().get(key);
			
			SnapshotCreature snapshot = new SnapshotCreature(
					completed
					, visiblyChanged
			);
			creatures.put(key, snapshot);
		}
		
		Snapshot completedTick = new Snapshot(tickNumber
			, Collections.unmodifiableMap(cuboids)
			, Collections.unmodifiableMap(entities)
			, Collections.unmodifiableMap(creatures)
			, completedHeightMaps
			
			// postedEvents
			, masterFragment.postedEvents
			// internallyMarkedAlive
			, masterFragment.internallyMarkedAlive
			
			// Stats.
			, new TickStats(tickNumber
				, startingMaterials.millisInTickPreamble
				, millisTickParallelPhase
				, millisTickPostamble
				, threadStats
				, masterFragment.crowd.committedMutationCount()
				, masterFragment.world.committedMutationCount()
			)
		);
		return completedTick;
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
			, Set<CuboidAddress> internallyMarkedAlive
			
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
			// Note that we only add of of these inputs if the entity has some mutations scheduled against it (may not
			// be ready, though).
			, Map<Integer, _InputEntity> changesToRun
			// Never null but typically empty.
			, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
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
			
			// Higher-level data associated with the materials.
			, EntityCollection entityCollection
			
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
	private static record _EntityMutationWrapper(EntityActionSimpleMove<IMutablePlayerEntity> mutation, long commitLevel) {}

	/**
	 * A wrapper over the IMutationEntity with associated entity ID.
	 */
	private static record _OperatorMutationWrapper(int entityId, IEntityAction<IMutablePlayerEntity> mutation) {}

	/**
	 * A wrapper over the per-thread partial data which we hand-off at synchronization.
	 */
	private static record _PartialHandoffData(_ProcessedFragment world
			, _ProcessedGroup crowd
			, _CreatureGroup creatures
			, List<CreatureEntity> spawnedCreatures
			, List<ScheduledMutation> newlyScheduledMutations
			, Map<Integer, List<ScheduledChange>> newlyScheduledChanges
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges
			, List<EventRecord> postedEvents
			, Set<CuboidAddress> internallyMarkedAlive
	) {}

	private static record _CreatureGroup(boolean ignored
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}

	private static record _ProcessedGroup(int committedMutationCount
		// We will pass back an OutputEntity for every InputEntity processed by this thread, even if no changes.
		, Map<Integer, _OutputEntity> entityOutput
	) {}

	// Note that NEITHER of these will be NULL and scheduledChanges MUST not be empty.
	private static record _InputEntity(Entity entity
		, List<ScheduledChange> scheduledChanges
	) {}

	// Note that "entity" will be NULL if unchanged and notYetReadyChanges will NEVER be NULL but may be empty.
	private static record _OutputEntity(Entity entity
		, List<ScheduledChange> notYetReadyChanges
	) {}

	private static record _ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, Map<CuboidAddress, CuboidHeightMap> heightFragment
			, List<ScheduledMutation> notYetReadyMutations
			, Map<CuboidAddress, Map<BlockAddress, Long>> periodicNotReadyByCuboid
			, Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid
			, int committedMutationCount
	) {}
}
