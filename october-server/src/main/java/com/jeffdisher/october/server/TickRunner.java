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
import com.jeffdisher.october.engine.EnginePassives;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.logic.PassiveIdAssigner;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SpatialIndex;
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
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
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
	private final PassiveIdAssigner _passiveIdAssigner;
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
			, PassiveIdAssigner passiveIdAssigner
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
		_passiveIdAssigner = passiveIdAssigner;
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
				ProcessorElement thisThread = new ProcessorElement(id, _syncPoint, atomic);
				_backgroundThreadMain(thisThread);
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
			, List.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, Set.of()
			
			, Map.of()
			
			, EntityCollection.emptyCollection()
			, null
			
			, 0L
			, System.currentTimeMillis()
		);
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, emptyMaterials
				, new _PartialHandoffData(new _ProcessedFragment(Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), 0)
						, new _ProcessedGroup(0, Map.of())
						, new _CreatureGroup(false, Map.of(), List.of())
						, new _PassiveGroup(false, Map.of(), List.of())
						, List.of()
						, List.of()
						, List.of()
						, Map.of()
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
			CommonMutationSink newMutationSink = new CommonMutationSink(materials.completedCuboids.keySet());
			CommonChangeSink newChangeSink = new CommonChangeSink(materials.completedEntities.keySet(), materials.completedCreatures.keySet(), materials.completedPassives.keySet());
			List<EventRecord> events = new ArrayList<>();
			
			// On the server, we just generate the tick time as purely abstract monotonic value.
			long currentTickTimeMillis = (materials.thisGameTick * _millisPerTick);
			
			// We will capture the newly-spawned creatures into a basic list.
			List<CreatureEntity> spawnedCreatures = new ArrayList<>();
			TickProcessingContext.ICreatureSpawner spawnConsumer = (EntityType type, EntityLocation location) -> {
				int id = _idAssigner.next();
				CreatureEntity entity = CreatureEntity.create(id, type, location, currentTickTimeMillis);
				spawnedCreatures.add(entity);
			};
			// Same with passives.
			List<PassiveEntity> spawnedPassives = new ArrayList<>();
			TickProcessingContext.IPassiveSpawner passiveConsumer = (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
				int id = _passiveIdAssigner.next();
				PassiveEntity entity = new PassiveEntity(id, type, location, velocity, extendedData, currentTickTimeMillis);
				spawnedPassives.add(entity);
			};
			// NOTE:  We only expose passive entities in the interface since we only have a use-case for them, at the moment.
			SpatialIndex.Builder builder = new SpatialIndex.Builder();
			for (PassiveEntity passive : thisTickMaterials.completedPassives.values())
			{
				if (PassiveType.ITEM_SLOT == passive.type())
				{
					builder.add(passive.id(), passive.location());
				}
			}
			SpatialIndex passiveSpatialIndex = builder.finish(PassiveType.ITEM_SLOT.volume());
			TickProcessingContext.IPassiveSearch passiveSearch = new TickProcessingContext.IPassiveSearch() {
				@Override
				public PartialPassive getById(int id)
				{
					PassiveEntity passive = thisTickMaterials.completedPassives.get(id);
					return (null != passive)
						? PartialPassive.fromPassive(passive)
						: null
					;
				}
				@Override
				public PartialPassive[] findPassiveItemSlotsInRegion(EntityLocation base, EntityLocation edge)
				{
					return passiveSpatialIndex.idsIntersectingRegion(base, edge).stream()
						.map((Integer id) -> {
							PassiveEntity passive = thisTickMaterials.completedPassives.get(id);
							return PartialPassive.fromPassive(passive);
						})
						.toArray((int size) -> new PartialPassive[size])
					;
				}
			};
			Set<CuboidAddress> internallyMarkedAlive = new HashSet<>();
			TickProcessingContext context = new TickProcessingContext(materials.thisGameTick
					, cachingLoader
					, (Integer entityId) -> (entityId > 0)
						? MinimalEntity.fromEntity(thisTickMaterials.completedEntities.get(entityId))
						: MinimalEntity.fromCreature(thisTickMaterials.completedCreatures.get(entityId))
					, passiveSearch
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
					, passiveConsumer
					, _random
					, (EventRecord event) -> events.add(event)
					, (CuboidAddress address) -> internallyMarkedAlive.add(address)
					, _config
					, _millisPerTick
					, currentTickTimeMillis
			);
			
			// We cluster work together in cuboid columns in order to improve per-thread world cache utilization and allow some result merging in the parallel phase.
			// First, we will handle the one-off special-cases.
			_runParallelSpecialCases(thisThread, materials, context);
			
			// Now, loop over the rest of the high-level units.
			_PartialHandoffData innerResults = _runParallelHighLevelUnits(thisThread, materials, context);
			
			materials = _mergeTickStateAndWaitForNext(thisThread
				, materials
				, new _PartialHandoffData(innerResults.world
					, innerResults.crowd
					, innerResults.creatures
					, innerResults.passives
					, spawnedCreatures
					, spawnedPassives
					, newMutationSink.takeExportedMutations()
					, newChangeSink.takeExportedChanges()
					, newChangeSink.takeExportedCreatureChanges()
					, newChangeSink.takeExportedPassiveActions()
					, events
					, internallyMarkedAlive
				)
			);
		}
	}

	private static void _runParallelSpecialCases(ProcessorElement thisThread
		, TickMaterials materials
		, TickProcessingContext context
	)
	{
		// We will have the first thread attempt the monster spawning algorithm.
		if (thisThread.handleNextWorkUnit())
		{
			long startNanos = System.nanoTime();
			
			// This will spawn in the context, if spawning is appropriate.
			EngineSpawner.trySpawnCreature(context
					, materials.entityCollection
					, materials.completedCuboids
					, materials.completedHeightMaps
					, materials.completedCreatures
			);
			
			long endNanos = System.nanoTime();
			thisThread.nanosInEngineSpawner = (endNanos - startNanos);
		}
		// We need to check the operator as a special-case since it isn't a real entity.
		if (thisThread.handleNextWorkUnit())
		{
			long startNanos = System.nanoTime();
			
			// Verify that this isn't redundantly described.
			Assert.assertTrue(!materials.completedEntities.containsKey(EnginePlayers.OPERATOR_ENTITY_ID));
			EnginePlayers.processOperatorActions(context, materials.operatorChanges);
			
			long endNanos = System.nanoTime();
			thisThread.nanosProcessingOperator = (endNanos - startNanos);
		}
	}

	private static _PartialHandoffData _runParallelHighLevelUnits(ProcessorElement thisThread
		, TickMaterials materials
		, TickProcessingContext context
	)
	{
		// TODO:  Replace this collection technique and return value with something more appropriate as this re-write progresses.
		List<_PartialHandoffData> partials = new ArrayList<>();
		_HighLevelPlan highLevel = materials.highLevel;
		
		// We will have a thread repackage anything which spilled.
		if (thisThread.handleNextWorkUnit())
		{
			if (!highLevel.spilledEntities.isEmpty())
			{
				Map<Integer, _OutputEntity> repackaged = new HashMap<>();
				for (Map.Entry<Integer, _InputEntity> elt : highLevel.spilledEntities.entrySet())
				{
					_InputEntity input = elt.getValue();
					// We set this to null output entity since that means it was unchanged.
					_OutputEntity output = new _OutputEntity(null, input.scheduledChanges);
					repackaged.put(elt.getKey(), output);
				}
				_ProcessedGroup spilledGroup = new _ProcessedGroup(0, repackaged);
				// Just use empty elements except for our spilled group.
				_PartialHandoffData spilledPartial = new _PartialHandoffData(new _ProcessedFragment(Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), 0)
					, spilledGroup
					, new _CreatureGroup(false, Map.of(), List.of())
					, new _PassiveGroup(false, Map.of(), List.of())
					, List.of()
					, List.of()
					, List.of()
					, Map.of()
					, Map.of()
					, Map.of()
					, List.of()
					, Set.of()
				);
				partials.add(spilledPartial);
			}
		}
		
		for (_CommonWorkUnit unit : highLevel.common)
		{
			if (thisThread.handleNextWorkUnit())
			{
				_PartialHandoffData result = _processWorkUnit(thisThread, materials, context, unit);
				partials.add(result);
			}
		}
		_PartialHandoffData finalResult = _mergeAndClearPartialFragments(partials.toArray((int size) -> new _PartialHandoffData[size]));
		return finalResult;
	}

	private static _PartialHandoffData _processWorkUnit(ProcessorElement processor
		, TickMaterials materials
		, TickProcessingContext context
		, _CommonWorkUnit unit
	)
	{
		// Per-cuboid data.
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> fragmentHeights = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> allCuboidHeights = new HashMap<>();
		Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		Map<CuboidAddress, Map<BlockAddress, Long>> periodicNotReadyByCuboid = new HashMap<>();
		int committedMutationCount = 0;
		
		// Per-player entity data.
		Map<Integer, _OutputEntity> processedEntities = new HashMap<>();
		int committedActionCount = 0;
		
		// Per-creature entity data.
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		
		// Per-passive entity data.
		Map<Integer, PassiveEntity> updatedPassives = new HashMap<>();
		List<Integer> deadPassiveIds = new ArrayList<>();
		
		// We need to walk the cuboids and collect data from each of them and associated players and creatures.
		processor.workUnitsProcessed += 1;
		Set<CuboidAddress> loadedCuboids = materials.completedCuboids.keySet();
		for (_CuboidWorkUnit subUnit : unit.cuboids)
		{
			// This is our element.
			processor.cuboidsProcessed += 1;
			IReadOnlyCuboidData oldState = subUnit.cuboid;
			CuboidAddress key = oldState.getCuboidAddress();
			
			// We can't be told to operate on something which isn't in the state.
			Assert.assertTrue(null != oldState);
			long startCuboidNanos = System.nanoTime();
			EngineCuboids.SingleCuboidResult cuboidResult = EngineCuboids.processOneCuboid(context
				, loadedCuboids
				, subUnit.mutations
				, subUnit.periodicMutationMillis
				, materials.modifiedBlocksByCuboidAddress
				, materials.potentialLightChangesByCuboid
				, materials.potentialLogicChangesByCuboid
				, materials.cuboidsLoadedThisTick
				, key
				, oldState
			);
			if (null != cuboidResult.changedCuboidOrNull())
			{
				fragment.put(key, cuboidResult.changedCuboidOrNull());
			}
			if (null != cuboidResult.changedHeightMap())
			{
				CuboidHeightMap changedHeightMap = cuboidResult.changedHeightMap();
				fragmentHeights.put(key, changedHeightMap);
				allCuboidHeights.put(key, changedHeightMap);
			}
			else
			{
				allCuboidHeights.put(key, subUnit.cuboidHeightMap);
			}
			if (null != cuboidResult.changedBlocks())
			{
				Assert.assertTrue(!cuboidResult.changedBlocks().isEmpty());
				blockChangesByCuboid.put(key, cuboidResult.changedBlocks());
			}
			if (null != cuboidResult.notYetReadyMutations())
			{
				notYetReadyMutations.addAll(cuboidResult.notYetReadyMutations());
			}
			if (null != cuboidResult.periodicNotReady())
			{
				Assert.assertTrue(!cuboidResult.periodicNotReady().isEmpty());
				periodicNotReadyByCuboid.put(key, cuboidResult.periodicNotReady());
			}
			long endCuboidNanos = System.nanoTime();
			processor.cuboidBlockupdatesProcessed += cuboidResult.blockUpdatesProcessed();
			processor.cuboidMutationsProcessed += cuboidResult.mutationsProcessed();
			processor.nanosInEngineCuboids += (endCuboidNanos - startCuboidNanos);
			committedMutationCount += cuboidResult.blockUpdatesApplied() + cuboidResult.mutationsApplied();
			
			// Process the player entities in this cuboid.
			for (_EntityWorkUnit entityUnit : subUnit.entities)
			{
				Entity entity = entityUnit.entity;
				List<ScheduledChange> changes = entityUnit.actions;
				processor.playersProcessed += 1;
				
				EnginePlayers.SinglePlayerResult result = EnginePlayers.processOnePlayer(context
					, materials.entityCollection
					, entity
					, changes
				);
				processedEntities.put(entity.id(), new _OutputEntity(result.changedEntityOrNull(), result.notYetReadyChanges()));
				processor.playerActionsProcessed += result.entityChangesProcessed();
				committedActionCount += result.committedMutationCount();
			}
			long endPlayerNanos = System.nanoTime();
			processor.nanosInEnginePlayers += (endPlayerNanos - endCuboidNanos);
			
			// Process the creature entities in this cuboid.
			for (_CreatureWorkUnit creatureUnit : subUnit.creatures)
			{
				CreatureEntity creature = creatureUnit.creature;
				List<IEntityAction<IMutableCreatureEntity>> changes = creatureUnit.actions;
				processor.creaturesProcessed += 1;
				processor.creatureActionsProcessed += changes.size();
				EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
					, materials.entityCollection
					, creature
					, changes
				);
				Integer id = creature.id();
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
					processor.creatureActionsProcessed += 1;
				}
			}
			long endCreatureNanos = System.nanoTime();
			processor.nanosInEngineCreatures += (endCreatureNanos - endPlayerNanos);
			
			// Process the passive entities in this cuboid.
			for (_PassiveWorkUnit passiveUnit : subUnit.passives)
			{
				PassiveEntity passive = passiveUnit.passive;
				List<IPassiveAction> actions = passiveUnit.actions;
				processor.passivesProcessed += 1;
				processor.passiveActionsProcessed += actions.size();
				PassiveEntity result = EnginePassives.processOneCreature(context, materials.entityCollection, passive, actions);
				Integer id = passive.id();
				if (null == result)
				{
					deadPassiveIds.add(id);
				}
				else if (result != passive)
				{
					updatedPassives.put(id, result);
				}
			}
			long endPassiveNanos = System.nanoTime();
			processor.nanosInEnginePassives += (endPassiveNanos - endCreatureNanos);
		}
		
		// We can now merge the height maps since each _CommonWorkUnit is a full column.
		Map<CuboidColumnAddress, ColumnHeightMap> columnHeight = HeightMapHelpers.buildColumnMaps(allCuboidHeights);
		// This should only be for this single column.
		Assert.assertTrue(1 == columnHeight.size());
		
		_ProcessedFragment world = new _ProcessedFragment(fragment
			, fragmentHeights
			, columnHeight
			, notYetReadyMutations
			, periodicNotReadyByCuboid
			, blockChangesByCuboid
			, committedMutationCount
		);
		_ProcessedGroup crowd = new _ProcessedGroup(committedActionCount
			, processedEntities
		);
		_CreatureGroup creatures = new _CreatureGroup(false
			, updatedCreatures
			, deadCreatureIds
		);
		_PassiveGroup passives = new _PassiveGroup(false
			, updatedPassives
			, deadPassiveIds
		);
		return new _PartialHandoffData(world
			, crowd
			, creatures
			, passives
			, List.of()
			, List.of()
			, List.of()
			, Map.of()
			, Map.of()
			, Map.of()
			, List.of()
			, Set.of()
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
			Map<Integer, PassiveEntity> mutablePassiveState = _extractSnapshotPassives(startingMaterials, masterFragment);
			
			Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState = new HashMap<>(startingMaterials.completedCuboids);
			mutableWorldState.putAll(masterFragment.world.stateFragment());
			Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = masterFragment.world.completedHeightMaps;
			
			Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = _extractSnapshotBlockMutations(masterFragment);
			Map<Integer, List<ScheduledChange>> snapshotEntityMutations = _extractPlayerEntityChanges(masterFragment);
			Set<Integer> updatedEntities = _extractChangedPlayerEntitiesOnly(masterFragment);
			Set<Integer> updatedCreatures = masterFragment.creatures.updatedCreatures.keySet();
			Set<Integer> updatedPassives = masterFragment.passives.updatedPassives.keySet();
			
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
				, mutablePassiveState
				, completedHeightMaps
				, snapshotBlockMutations
				, snapshotEntityMutations
				, updatedEntities
				, updatedCreatures
				, updatedPassives
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
				_addCreaturesInNewCuboidsWithMillis(mutableCreatureState, newCuboids);
				_addPassivesInNewCuboidsWithMillis(mutablePassiveState, newCuboids);
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
				Map<Integer, List<IPassiveAction>> nextPassiveActions = masterFragment.newlyScheduledPassiveActions;
				
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
						// Similarly, remove the passives.
						Iterator<Map.Entry<Integer, PassiveEntity>> expensivePassives = mutablePassiveState.entrySet().iterator();
						while (expensivePassives.hasNext())
						{
							Map.Entry<Integer, PassiveEntity> one = expensivePassives.next();
							EntityLocation loc = one.getValue().location();
							if (loc.getBlockLocation().getCuboidAddress().equals(address))
							{
								expensivePassives.remove();
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
						
						// Remove any of the scheduled operations against this entity.
						nextTickChanges.remove(entityId);
					}
				}
				
				// TODO:  We should probably remove this once we are sure we know what is happening and/or find a cheaper way to check this.
				for (CuboidAddress key : pendingMutations.keySet())
				{
					// Given that these can only be scheduled against loaded cuboids, which can only be explicitly unloaded above, anything remaining must still be present.
					Assert.assertTrue(mutableWorldState.containsKey(key));
				}
				for (CuboidAddress key : periodicMutations.keySet())
				{
					// Given that these can only be scheduled against loaded cuboids, which can only be explicitly unloaded above, anything remaining must still be present.
					Assert.assertTrue(mutableWorldState.containsKey(key));
				}
				for (int entityId : nextTickChanges.keySet())
				{
					// Given that these can only be scheduled against loaded entities, which can only be explicitly unloaded above, anything remaining must still be present.
					Assert.assertTrue(mutableCrowdState.containsKey(entityId));
				}
				
				// Convert this raw next tick action accumulation into the CrowdProcessor input.
				Map<Integer, _InputEntity> changesToRun = _determineChangesToRun(mutableCrowdState, nextTickChanges);
				
				// We want to build the arrangement of blocks modified in the last tick so that block updates can be synthesized.
				Map<CuboidAddress, List<AbsoluteLocation>> updatedBlockLocationsByCuboid = _extractBlockUpdateLocations( masterFragment);
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid = _extractPotentialLightChanges( masterFragment);
				Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid = _extractPotentialLogicChanges( masterFragment);
				
				// WARNING:  completedHeightMaps does NOT include the new height maps loaded after the previous tick finished!
				// (this is done to avoid the cost of rebuilding the maps since the column height maps are not guaranteed to be fully accurate)
				EntityCollection entityCollection = EntityCollection.fromMaps(mutableCrowdState, mutableCreatureState);
				_HighLevelPlan highLevelPlan = _packageHighLevelWorkUnits(mutableWorldState
					, nextTickMutableHeightMaps
					, completedHeightMaps
					, changesToRun
					, mutableCreatureState
					, mutablePassiveState
					, pendingMutations
					, periodicMutations
					, nextCreatureChanges
					, nextPassiveActions
				);
				
				// Collect the last timing data for this tick preamble.
				long endMillisPreamble = System.currentTimeMillis();
				long millisInNextTickPreamble = endMillisPreamble - startMillisPreamble;
				
				_thisTickMaterials = new TickMaterials(_nextTick
						, Collections.unmodifiableMap(mutableWorldState)
						, Collections.unmodifiableMap(nextTickMutableHeightMaps)
						, completedHeightMaps
						, Collections.unmodifiableMap(mutableCrowdState)
						// completedCreatures
						, Collections.unmodifiableMap(mutableCreatureState)
						, Collections.unmodifiableMap(mutablePassiveState)
						
						, operatorChanges
						, updatedBlockLocationsByCuboid
						, potentialLightChangesByCuboid
						, potentialLogicChangesByCuboid
						, cuboidsLoadedThisTick
						
						// Data only used by this method:
						, newCommitLevels
						
						, entityCollection
						, highLevelPlan
						
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

	private static Map<Integer, PassiveEntity> _extractSnapshotPassives(TickMaterials startingMaterials, _PartialHandoffData masterFragment)
	{
		Map<Integer, PassiveEntity> mutablePassiveState = new HashMap<>(startingMaterials.completedPassives);
		mutablePassiveState.putAll(masterFragment.passives.updatedPassives());
		for (Integer creatureId : masterFragment.passives.deadPassiveIds())
		{
			mutablePassiveState.remove(creatureId);
		}
		for (PassiveEntity newPassive : masterFragment.spawnedPassives())
		{
			mutablePassiveState.put(newPassive.id(), newPassive);
		}
		return mutablePassiveState;
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

	private static Set<Integer> _extractChangedPlayerEntitiesOnly(_PartialHandoffData masterFragment)
	{
		Set<Integer> updatedEntities = new HashSet<>();
		for (Map.Entry<Integer, _OutputEntity> processed : masterFragment.crowd.entityOutput().entrySet())
		{
			_OutputEntity value = processed.getValue();
			Entity updated = value.entity();
			
			// Note that this is documented to be null if nothing changed.
			if (null != updated)
			{
				Integer key = processed.getKey();
				updatedEntities.add(key);
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
	private static void _addCreaturesInNewCuboidsWithMillis(Map<Integer, CreatureEntity> out_mutableCreatureState, List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids)
	{
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				// Load any creatures associated with this cuboid.
				for (CreatureEntity loadedCreature : suspended.creatures())
				{
					out_mutableCreatureState.put(loadedCreature.id(), loadedCreature);
				}
			}
		}
	}

	// NOTE:  Modifies out_mutablePassiveState.
	private static void _addPassivesInNewCuboidsWithMillis(Map<Integer, PassiveEntity> out_mutablePassiveState, List<SuspendedCuboid<IReadOnlyCuboidData>> newCuboids)
	{
		if (null != newCuboids)
		{
			for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
			{
				// Load any creatures associated with this cuboid.
				for (PassiveEntity loadedPassive : suspended.passives())
				{
					out_mutablePassiveState.put(loadedPassive.id(), loadedPassive);
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
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = new HashMap<>();
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
		
		// EnginePassives
		Map<Integer, PassiveEntity> updatedPassives = new HashMap<>();
		List<Integer> deadPassiveIds = new ArrayList<>();
		
		List<CreatureEntity> spawnedCreatures = new ArrayList<>();
		List<PassiveEntity> spawnedPassives = new ArrayList<>();
		List<ScheduledMutation> newlyScheduledMutations = new ArrayList<>();
		Map<Integer, List<ScheduledChange>> newlyScheduledChanges = new HashMap<>();
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges = new HashMap<>();
		Map<Integer, List<IPassiveAction>> newlyScheduledPassiveActions = new HashMap<>();
		List<EventRecord> postedEvents = new ArrayList<>();
		Set<CuboidAddress> internallyMarkedAlive = new HashSet<>();
		
		for (int i = 0; i < partials.length; ++i)
		{
			_PartialHandoffData fragment = partials[i];
			
			// EngineCuboids.ProcessedFragment world
			stateFragment.putAll(fragment.world().stateFragment());
			heightFragment.putAll(fragment.world().heightFragment());
			completedHeightMaps.putAll(fragment.world().completedHeightMaps());
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
			
			// EnginePassives
			updatedPassives.putAll(fragment.passives().updatedPassives());
			deadPassiveIds.addAll(fragment.passives().deadPassiveIds());
			
			spawnedCreatures.addAll(fragment.spawnedCreatures());
			spawnedPassives.addAll(fragment.spawnedPassives());
			newlyScheduledMutations.addAll(fragment.newlyScheduledMutations());
			newlyScheduledChanges.putAll(fragment.newlyScheduledChanges());
			newlyScheduledCreatureChanges.putAll(fragment.newlyScheduledCreatureChanges());
			newlyScheduledPassiveActions.putAll(fragment.newlyScheduledPassiveActions());
			postedEvents.addAll(fragment.postedEvents());
			internallyMarkedAlive.addAll(fragment.internallyMarkedAlive());
			partials[i] = null;
		}
		
		_ProcessedFragment world = new _ProcessedFragment(stateFragment
			, heightFragment
			, completedHeightMaps
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
		_PassiveGroup passives = new _PassiveGroup(false
			, updatedPassives
			, deadPassiveIds
		);
		return new _PartialHandoffData(world
			, crowd
			, creatures
			, passives
			, spawnedCreatures
			, spawnedPassives
			, newlyScheduledMutations
			, newlyScheduledChanges
			, newlyScheduledCreatureChanges
			, newlyScheduledPassiveActions
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
		, Map<Integer, PassiveEntity> mutablePassiveState
		, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
		, Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations
		, Map<Integer, List<ScheduledChange>> snapshotEntityMutations
		, Set<Integer> updatedEntities
		, Set<Integer> updatedCreatures
		, Set<Integer> updatedPassives
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
			Entity previousVersionOrNull = updatedEntities.contains(key)
				? startingMaterials.completedEntities.get(key)
				: null
			;
			long commitLevel = startingMaterials.commitLevels.get(key);
			
			// Get the scheduled mutations (note that this is often null but we don't want to store null).
			List<ScheduledChange> scheduledMutations = snapshotEntityMutations.get(key);
			if (null == scheduledMutations)
			{
				scheduledMutations = List.of();
			}
			SnapshotEntity snapshot = new SnapshotEntity(
					completed
					, previousVersionOrNull
					, commitLevel
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
			CreatureEntity previousVersionOrNull = updatedCreatures.contains(key)
				? startingMaterials.completedCreatures.get(key)
				: null
			;
			
			SnapshotCreature snapshot = new SnapshotCreature(
				completed
				, previousVersionOrNull
			);
			creatures.put(key, snapshot);
		}
		Map<Integer, SnapshotPassive> passives = new HashMap<>();
		for (Map.Entry<Integer, PassiveEntity>  ent : mutablePassiveState.entrySet())
		{
			Integer key = ent.getKey();
			// Passives are expected to have positive IDs.
			Assert.assertTrue(key > 0);
			PassiveEntity completed = ent.getValue();
			PassiveEntity previousVersionOrNull = updatedPassives.contains(key)
				? startingMaterials.completedPassives.get(key)
				: null
			;
			
			SnapshotPassive snapshot = new SnapshotPassive(
				completed
				, previousVersionOrNull
			);
			passives.put(key, snapshot);
		}
		
		Snapshot completedTick = new Snapshot(tickNumber
			, Collections.unmodifiableMap(cuboids)
			, Collections.unmodifiableMap(entities)
			, Collections.unmodifiableMap(creatures)
			, Collections.unmodifiableMap(passives)
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

	private static _HighLevelPlan _packageHighLevelWorkUnits(Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
		, Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps
		, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
		, Map<Integer, _InputEntity> entities
		, Map<Integer, CreatureEntity> completedCreatures
		, Map<Integer, PassiveEntity> completedPassives
		, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
		, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationMillis
		, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges
		, Map<Integer, List<IPassiveAction>> passiveActions
	)
	{
		Map<Integer, _InputEntity> spilledEntities = new HashMap<>();
		Map<CuboidAddress, List<_EntityWorkUnit>> workingEntityList = new HashMap<>();
		for (_InputEntity entity : entities.values())
		{
			CuboidAddress thisAddress = entity.entity.location().getBlockLocation().getCuboidAddress();
			if (completedCuboids.containsKey(thisAddress))
			{
				List<ScheduledChange> scheduledChanges = entity.scheduledChanges;
				if (!workingEntityList.containsKey(thisAddress))
				{
					workingEntityList.put(thisAddress, new ArrayList<>());
				}
				_EntityWorkUnit unit = new _EntityWorkUnit(entity.entity
					, scheduledChanges
				);
				List<_EntityWorkUnit> list = workingEntityList.get(thisAddress);
				list.add(unit);
			}
			else
			{
				// This cuboid isn't loaded so spill it to the next tick.
				// This is usually due to load ordering (an entity might be created before its cuboid is loaded - thus dropping the creation of its periodic mutation).
				spilledEntities.put(entity.entity.id(), entity);
			}
		}
		Map<CuboidAddress, List<_CreatureWorkUnit>> workingCreatureList = new HashMap<>();
		for (CreatureEntity entity : completedCreatures.values())
		{
			CuboidAddress thisAddress = entity.location().getBlockLocation().getCuboidAddress();
			//  Note that we expect any creatures who weren't unloaded to be in a loaded cuboid.
			Assert.assertTrue(completedCuboids.containsKey(thisAddress));
			
			if (!workingCreatureList.containsKey(thisAddress))
			{
				workingCreatureList.put(thisAddress, new ArrayList<>());
			}
			List<IEntityAction<IMutableCreatureEntity>> actions = creatureChanges.get(entity.id());
			if (null == actions)
			{
				actions = List.of();
			}
			_CreatureWorkUnit unit = new _CreatureWorkUnit(entity
				, actions
			);
			List<_CreatureWorkUnit> list = workingCreatureList.get(thisAddress);
			list.add(unit);
		}
		Map<CuboidAddress, List<_PassiveWorkUnit>> workingPassiveList = new HashMap<>();
		for (PassiveEntity entity : completedPassives.values())
		{
			CuboidAddress thisAddress = entity.location().getBlockLocation().getCuboidAddress();
			//  Note that we expect any creatures who weren't unloaded to be in a loaded cuboid.
			Assert.assertTrue(completedCuboids.containsKey(thisAddress));
			
			if (!workingPassiveList.containsKey(thisAddress))
			{
				workingPassiveList.put(thisAddress, new ArrayList<>());
			}
			List<IPassiveAction> actions = passiveActions.get(entity.id());
			if (null == actions)
			{
				actions = List.of();
			}
			_PassiveWorkUnit unit = new _PassiveWorkUnit(entity
				, actions
			);
			List<_PassiveWorkUnit> list = workingPassiveList.get(thisAddress);
			list.add(unit);
		}
		
		Map<CuboidColumnAddress, List<_CuboidWorkUnit>> workingWorkList = new HashMap<>();
		for (CuboidAddress address : completedCuboids.keySet())
		{
			IReadOnlyCuboidData cuboid = completedCuboids.get(address);
			CuboidHeightMap cuboidHeightMap = cuboidHeightMaps.get(address);
			List<ScheduledMutation> mutations = mutationsToRun.get(address);
			if (null == mutations)
			{
				mutations = List.of();
			}
			Map<BlockAddress, Long> periodic = periodicMutationMillis.get(address);
			if (null == periodic)
			{
				periodic = Map.of();
			}
			List<_EntityWorkUnit> entityList = workingEntityList.get(address);
			if (null == entityList)
			{
				entityList = List.of();
			}
			List<_CreatureWorkUnit> creatures = workingCreatureList.get(address);
			if (null == creatures)
			{
				creatures = List.of();
			}
			List<_PassiveWorkUnit> passives = workingPassiveList.get(address);
			if (null == passives)
			{
				passives = List.of();
			}
			_CuboidWorkUnit unit = new _CuboidWorkUnit(cuboid
				, cuboidHeightMap
				, Collections.unmodifiableList(mutations)
				, Collections.unmodifiableMap(periodic)
				, Collections.unmodifiableList(entityList)
				, Collections.unmodifiableList(creatures)
				, Collections.unmodifiableList(passives)
			);
			
			CuboidColumnAddress column = address.getColumn();
			if (!workingWorkList.containsKey(column))
			{
				workingWorkList.put(column, new ArrayList<>());
			}
			List<_CuboidWorkUnit> list = workingWorkList.get(column);
			list.add(unit);
		}
		
		List<_CommonWorkUnit> result = new ArrayList<>();
		for (CuboidColumnAddress column : workingWorkList.keySet())
		{
			ColumnHeightMap columnHeightMap = completedHeightMaps.get(column);
			List<_CuboidWorkUnit> list = workingWorkList.get(column);
			if (null == list)
			{
				list = List.of();
			}
			// We will come with a priority hint based on the work here so that we can schedule heavier work units first:
			// +1 for each entry in the list, since it does require some checks just for existing (and potentially heavy lighting work)
			// +1 for each mutation applied to the cuboid
			// +1 for each creature in the cuboid (since they may have actions or other AI work to do)
			// +1 for each entity in the cuboid (since there may be actions from players)
			int priorityHint = list.stream().mapToInt(
				(_CuboidWorkUnit inner) -> 1 + inner.mutations.size() + inner.creatures.size() + inner.entities.size()
			).sum();
			_CommonWorkUnit unit = new _CommonWorkUnit(column
				, columnHeightMap
				, Collections.unmodifiableList(list)
				, priorityHint
			);
			result.add(unit);
		}
		// Now sort by priority list (descending on priorityHint).
		result.sort((_CommonWorkUnit one, _CommonWorkUnit two) -> two.priorityHint - one.priorityHint);
		return new _HighLevelPlan(Collections.unmodifiableList(result)
			, Collections.unmodifiableMap(spilledEntities)
		);
	}


	/**
	 * The snapshot of immutable state created whenever a tick is completed.
	 */
	public static record Snapshot(long tickNumber
			, Map<CuboidAddress, SnapshotCuboid> cuboids
			, Map<Integer, SnapshotEntity> entities
			, Map<Integer, SnapshotCreature> creatures
			, Map<Integer, SnapshotPassive> passives
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
			// The version of the entity at the end of the tick (never null).
			Entity completed
			// The previous version of the entity (null if not changed in this tick).
			, Entity previousVersion
			// The last commit level from the connected client.
			, long commitLevel
			// Never null but can be empty.
			, List<ScheduledChange> scheduledMutations
	)
	{}

	public static record SnapshotCreature(
			// Never null.
			CreatureEntity completed
			// The previous version of the entity (null if not changed in this tick).
			, CreatureEntity previousVersion
	)
	{}

	public static record SnapshotPassive(
			// Never null.
			PassiveEntity completed
			// The previous version of the entity (null if not changed in this tick).
			, PassiveEntity previousVersion
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
			long nanosPerMilli = 1_000_000L;
			long preamble = this.millisTickPreamble;
			long parallel = this.millisTickParallelPhase;
			long postamble = this.millisTickPostamble;
			long tickTime = preamble + parallel + postamble;
			out.println("Log for slow (" + tickTime + " ms) tick " + this.tickNumber);
			out.println("\tPreamble: " + preamble + " ms");
			out.println("\tParallel: " + parallel + " ms");
			for (int i = 0; i < this.threadStats.length; ++i)
			{
				ProcessorElement.PerThreadStats thread = this.threadStats[i];
				long millisInEnginePlayers = thread.nanosInEnginePlayers() / nanosPerMilli;
				long millisInEngineCreatures = thread.nanosInEngineCreatures() / nanosPerMilli;
				long millisInEnginePassives = thread.nanosInEnginePassives() / nanosPerMilli;
				long millisInEngineCuboids = thread.nanosInEngineCuboids() / nanosPerMilli;
				long millisInEngineSpawner = thread.nanosInEngineSpawner() / nanosPerMilli;
				long millisProcessingOperator = thread.nanosProcessingOperator() / nanosPerMilli;
				out.printf("\t-Thread %d ran %d work units in %d ms\n", i, thread.workUnitsProcessed(), (millisInEnginePlayers + millisInEngineCreatures + millisInEngineCuboids + millisInEngineSpawner + millisProcessingOperator));
				out.printf("\t\t=%d ms in EnginePlayer: %d players, %d actions\n", millisInEnginePlayers, thread.playersProcessed(), thread.playerActionsProcessed());
				out.printf("\t\t=%d ms in EngineCreatures: %d creatures, %d actions\n", millisInEngineCreatures, thread.creaturesProcessed(), thread.creatureActionsProcessed());
				out.printf("\t\t=%d ms in EnginePassives: %d passives, %d actions\n", millisInEnginePassives, thread.passivesProcessed(), thread.passiveActionsProcessed());
				out.printf("\t\t=%d ms in EngineCuboids: %d cuboids, %d mutations, %d block updates\n", millisInEngineCuboids, thread.cuboidsProcessed(), thread.cuboidMutationsProcessed(), thread.cuboidBlockupdatesProcessed());
				if (millisInEngineSpawner > 0L)
				{
					out.printf("\t\t=%d ms in EngineSpawner\n", millisInEngineSpawner);
				}
				if (millisProcessingOperator > 0L)
				{
					out.printf("\t\t=%d ms running operator commands\n", millisProcessingOperator);
				}
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
			// Read-only versions of the passives from the previous tick (by ID).
			, Map<Integer, PassiveEntity> completedPassives
			// Never null but typically empty.
			, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
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
			, _HighLevelPlan highLevel
			
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
			, _PassiveGroup passives
			, List<CreatureEntity> spawnedCreatures
			, List<PassiveEntity> spawnedPassives
			, List<ScheduledMutation> newlyScheduledMutations
			, Map<Integer, List<ScheduledChange>> newlyScheduledChanges
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges
			, Map<Integer, List<IPassiveAction>> newlyScheduledPassiveActions
			, List<EventRecord> postedEvents
			, Set<CuboidAddress> internallyMarkedAlive
	) {}

	private static record _CreatureGroup(boolean ignored
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}

	private static record _PassiveGroup(boolean ignored
			// Note that we will only pass back a new PassiveEntity object if it changed.
			, Map<Integer, PassiveEntity> updatedPassives
			, List<Integer> deadPassiveIds
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
			, Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps
			, List<ScheduledMutation> notYetReadyMutations
			, Map<CuboidAddress, Map<BlockAddress, Long>> periodicNotReadyByCuboid
			, Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid
			, int committedMutationCount
	) {}

	private static record _HighLevelPlan(List<_CommonWorkUnit> common
		, Map<Integer, _InputEntity> spilledEntities
	) {}

	private static record _CommonWorkUnit(CuboidColumnAddress columnAddress
		, ColumnHeightMap columnHeightMap
		, List<_CuboidWorkUnit> cuboids
		, int priorityHint
	) {}

	private static record _CuboidWorkUnit(IReadOnlyCuboidData cuboid
		, CuboidHeightMap cuboidHeightMap
		, List<ScheduledMutation> mutations
		, Map<BlockAddress, Long> periodicMutationMillis
		, List<_EntityWorkUnit> entities
		, List<_CreatureWorkUnit> creatures
		, List<_PassiveWorkUnit> passives
	) {}

	private static record _EntityWorkUnit(Entity entity
		, List<ScheduledChange> actions
	) {}

	private static record _CreatureWorkUnit(CreatureEntity creature
		, List<IEntityAction<IMutableCreatureEntity>> actions
	) {}

	private static record _PassiveWorkUnit(PassiveEntity passive
		, List<IPassiveAction> actions
	) {}
}
