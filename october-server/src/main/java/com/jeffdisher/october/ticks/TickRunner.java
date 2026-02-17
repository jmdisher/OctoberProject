package com.jeffdisher.october.ticks;

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
import com.jeffdisher.october.types.TargetedAction;
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
	private final Consumer<TickSnapshot> _tickCompletionListener;
	private final WorldConfig _config;

	// Read-only snapshot of the previously-completed tick.
	private TickSnapshot _snapshot;
	
	// Data which is part of "shared state" between external threads and the internal threads.
	private List<SuspendedCuboid<IReadOnlyCuboidData>> _newCuboids;
	private Set<CuboidAddress> _cuboidsToDrop;
	private final Map<Integer, PerEntitySharedAccess> _entitySharedAccess;
	private List<SuspendedEntity> _newEntities;
	private List<Integer> _departedEntityIds;
	private List<_OperatorMutationWrapper> _operatorMutations;
	
	// Ivars which are related to the interlock where the threads merge partial results and wait to start again.
	private TickMaterials _thisTickMaterials;
	private final TickOutput[] _partial;
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
			, Consumer<TickSnapshot> tickCompletionListener
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
		_partial = new TickOutput[threadCount];
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
		_snapshot = new TickSnapshot(0L
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
			, new TickSnapshot.TickStats(0L
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
	public synchronized TickSnapshot waitForPreviousTick()
	{
		// We just wait for the previous tick and don't start the next.
		return _locked_waitForTickComplete();
	}

	/**
	 * Requests that another tick be run, waiting for the previous one to complete if it is still running.
	 * Note that this function returns before the next tick completes.
	 * @return Returns the snapshot of the now-completed tick.
	 */
	public synchronized TickSnapshot startNextTick()
	{
		// Wait for the previous tick to complete.
		TickSnapshot snapshot = _locked_waitForTickComplete();
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
		TickMaterials materials = _mergeTickStateAndWaitForNext(thisThread
				, new TickOutput(new TickOutput.WorldOutput(List.of(), List.of(), List.of(), 0)
						, new TickOutput.EntitiesOutput(0, List.of())
						, new TickOutput.CreaturesOutput(false, List.of())
						, new TickOutput.PassivesOutput(false, List.of())
						, List.of()
						, List.of()
						, List.of()
						, List.of()
						, List.of()
						, List.of()
						, List.of()
						, Set.of()
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
				BlockProxy proxy = thisTickMaterials.previousProxyCache.get(location);
				if (null == proxy)
				{
					CuboidAddress address = location.getCuboidAddress();
					IReadOnlyCuboidData cuboid = thisTickMaterials.completedCuboids.get(address);
					proxy = (null != cuboid)
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
					;
				}
				return proxy;
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
			TickOutput innerResults = _runParallelHighLevelUnits(thisThread, materials, context);
			
			materials = _mergeTickStateAndWaitForNext(thisThread
				, new TickOutput(innerResults.world()
					, innerResults.entities()
					, innerResults.creatures()
					, innerResults.passives()
					, spawnedCreatures
					, spawnedPassives
					, newMutationSink.takeExportedMutations()
					, newChangeSink.takeExportedChanges()
					, newChangeSink.takeExportedCreatureChanges()
					, newChangeSink.takeExportedPassiveActions()
					, events
					, internallyMarkedAlive
					, cachingLoader.extractCache()
				)
				, materials.millisInTickPreamble
				, materials.timeMillisPreambleEnd
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

	private static TickOutput _runParallelHighLevelUnits(ProcessorElement thisThread
		, TickMaterials materials
		, TickProcessingContext context
	)
	{
		// TODO:  Replace this collection technique and return value with something more appropriate as this re-write progresses.
		List<TickOutput> partials = new ArrayList<>();
		TickInput highLevel = materials.highLevel;
		
		// We will have a thread repackage anything which spilled.
		if (thisThread.handleNextWorkUnit())
		{
			if (!highLevel.entitiesInUnloadedCuboids().isEmpty())
			{
				List<TickOutput.EntityOutput> repackaged = new ArrayList<>();
				for (TickInput.EntityInput input : highLevel.entitiesInUnloadedCuboids())
				{
					// We set this to null output entity since that means it was unchanged.
					TickOutput.EntityOutput output = new TickOutput.EntityOutput(input.entity().id()
						, input.entity()
						, null
						, input.unsortedActions()
						, input.clientCommitLevel()
					);
					repackaged.add(output);
				}
				TickOutput.EntitiesOutput spilledGroup = new TickOutput.EntitiesOutput(0, repackaged);
				// Just use empty elements except for our spilled group.
				TickOutput spilledPartial = new TickOutput(new TickOutput.WorldOutput(List.of(), List.of(), List.of(), 0)
					, spilledGroup
					, new TickOutput.CreaturesOutput(false, List.of())
					, new TickOutput.PassivesOutput(false, List.of())
					, List.of()
					, List.of()
					, List.of()
					, List.of()
					, List.of()
					, List.of()
					, List.of()
					, Set.of()
					, Map.of()
				);
				partials.add(spilledPartial);
			}
		}
		
		for (TickInput.ColumnInput unit : highLevel.columns())
		{
			if (thisThread.handleNextWorkUnit())
			{
				TickOutput result = _processWorkUnit(thisThread, materials, context, unit);
				partials.add(result);
			}
		}
		TickOutput finalResult = _mergeAndClearPartialFragments(partials.toArray((int size) -> new TickOutput[size]));
		return finalResult;
	}

	private static TickOutput _processWorkUnit(ProcessorElement processor
		, TickMaterials materials
		, TickProcessingContext context
		, TickInput.ColumnInput unit
	)
	{
		// Collect data for _ProcessedFragment.
		List<TickOutput.CuboidOutput> cuboids = new ArrayList<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		int committedMutationCount = 0;
		
		// Per-player entity data.
		List<TickOutput.EntityOutput> processedEntities = new ArrayList<>();
		int committedActionCount = 0;
		
		// Per-creature entity data.
		List<TickOutput.BasicOutput<CreatureEntity>> updatedCreatures = new ArrayList<>();
		
		// Per-passive entity data.
		List<TickOutput.BasicOutput<PassiveEntity>> updatedPassives = new ArrayList<>();
		
		// We need to walk the cuboids and collect data from each of them and associated players and creatures.
		processor.workUnitsProcessed += 1;
		Set<CuboidAddress> loadedCuboids = materials.completedCuboids.keySet();
		Map<CuboidAddress, CuboidHeightMap> existingAndUpdatedCuboidHeightMaps = new HashMap<>();
		for (TickInput.CuboidInput subUnit : unit.cuboids())
		{
			// This is our element.
			processor.cuboidsProcessed += 1;
			IReadOnlyCuboidData previousCuboid = subUnit.cuboid();
			CuboidHeightMap previousHeightMap = subUnit.cuboidHeightMap();
			CuboidAddress cuboidAddress = previousCuboid.getCuboidAddress();
			
			// We can't be told to operate on something which isn't in the state.
			Assert.assertTrue(null != previousCuboid);
			Assert.assertTrue(null != previousHeightMap);
			
			long startCuboidNanos = System.nanoTime();
			EngineCuboids.SingleCuboidResult cuboidResult = EngineCuboids.processOneCuboid(context
				, loadedCuboids
				, subUnit.mutations()
				, subUnit.periodicMutationMillis()
				, materials.modifiedBlocksByCuboidAddress
				, materials.potentialLightChangesByCuboid
				, materials.potentialLogicChangesByCuboid
				, materials.cuboidsLoadedThisTick
				, cuboidAddress
				, previousCuboid
			);
			IReadOnlyCuboidData updatedCuboidOrNull = cuboidResult.changedCuboidOrNull();
			CuboidHeightMap updatedHeightMapOrNull = cuboidResult.changedHeightMap();
			Map<BlockAddress, Long> periodicNotReadyMutations = (null != cuboidResult.periodicNotReady())
				? cuboidResult.periodicNotReady()
				: Map.of()
			;
			List<BlockChangeDescription> blockChanges = (null != cuboidResult.changedBlocks())
				? cuboidResult.changedBlocks()
				: List.of()
			;
			TickOutput.CuboidOutput outputCuboid = new TickOutput.CuboidOutput(cuboidAddress
				, previousCuboid
				, updatedCuboidOrNull
				, previousHeightMap
				, updatedHeightMapOrNull
				, periodicNotReadyMutations
				, blockChanges
			);
			cuboids.add(outputCuboid);
			
			// We need to choose which height map to use for the cuboid in order to build the map for the column.
			if (null != updatedHeightMapOrNull)
			{
				existingAndUpdatedCuboidHeightMaps.put(cuboidAddress, updatedHeightMapOrNull);
			}
			else
			{
				existingAndUpdatedCuboidHeightMaps.put(cuboidAddress, previousHeightMap);
			}
			
			// We track the not-yet-ready mutations globally, since they can be scheduled against anything, not just this cuboid.
			if (null != cuboidResult.notYetReadyMutations())
			{
				notYetReadyMutations.addAll(cuboidResult.notYetReadyMutations());
			}
			
			long endCuboidNanos = System.nanoTime();
			processor.cuboidBlockupdatesProcessed += cuboidResult.blockUpdatesProcessed();
			processor.cuboidMutationsProcessed += cuboidResult.mutationsProcessed();
			processor.nanosInEngineCuboids += (endCuboidNanos - startCuboidNanos);
			committedMutationCount += cuboidResult.blockUpdatesApplied() + cuboidResult.mutationsApplied();
			
			// Process the player entities in this cuboid.
			for (TickInput.EntityInput entityUnit : subUnit.entities())
			{
				Entity entity = entityUnit.entity();
				List<ScheduledChange> changes = entityUnit.unsortedActions();
				processor.playersProcessed += 1;
				
				EnginePlayers.SinglePlayerResult result = EnginePlayers.processOnePlayer(context
					, materials.entityCollection
					, entity
					, changes
				);
				processedEntities.add(new TickOutput.EntityOutput(entity.id()
					, entity
					, result.changedEntityOrNull()
					, result.notYetReadyChanges()
					, entityUnit.clientCommitLevel()
				));
				processor.playerActionsProcessed += result.entityChangesProcessed();
				committedActionCount += result.committedMutationCount();
			}
			long endPlayerNanos = System.nanoTime();
			processor.nanosInEnginePlayers += (endPlayerNanos - endCuboidNanos);
			
			// Process the creature entities in this cuboid.
			for (TickInput.CreatureInput creatureUnit : subUnit.creatures())
			{
				CreatureEntity creature = creatureUnit.creature();
				List<IEntityAction<IMutableCreatureEntity>> changes = creatureUnit.actions();
				processor.creaturesProcessed += 1;
				processor.creatureActionsProcessed += changes.size();
				EngineCreatures.SingleCreatureResult result = EngineCreatures.processOneCreature(context
					, materials.entityCollection
					, creature
					, changes
				);
				
				boolean didDie = (null == result.updatedEntity());
				boolean wasUpdated = !didDie && (result.updatedEntity() != creature);
				TickOutput.BasicOutput<CreatureEntity> output = new TickOutput.BasicOutput<>(creature.id()
					, creature
					, wasUpdated ? result.updatedEntity() : null
					, didDie
				);
				updatedCreatures.add(output);
				if (!result.didTakeSpecialAction())
				{
					processor.creatureActionsProcessed += 1;
				}
			}
			long endCreatureNanos = System.nanoTime();
			processor.nanosInEngineCreatures += (endCreatureNanos - endPlayerNanos);
			
			// Process the passive entities in this cuboid.
			for (TickInput.PassiveInput passiveUnit : subUnit.passives())
			{
				PassiveEntity passive = passiveUnit.passive();
				List<IPassiveAction> actions = passiveUnit.actions();
				processor.passivesProcessed += 1;
				processor.passiveActionsProcessed += actions.size();
				PassiveEntity result = EnginePassives.processOneCreature(context, materials.entityCollection, passive, actions);
				
				boolean didDie = (null == result);
				boolean wasUpdated = !didDie && (result != passive);
				TickOutput.BasicOutput<PassiveEntity> output = new TickOutput.BasicOutput<>(passive.id()
					, passive
					, wasUpdated ? result : null
					, didDie
				);
				updatedPassives.add(output);
			}
			long endPassiveNanos = System.nanoTime();
			processor.nanosInEnginePassives += (endPassiveNanos - endCreatureNanos);
		}
		
		// We can now merge the height maps since each _CommonWorkUnit is a full column.
		ColumnHeightMap columnHeightMap = HeightMapHelpers.buildSingleColumn(existingAndUpdatedCuboidHeightMaps);
		TickOutput.ColumnHeightOutput outputColumnHeight = new TickOutput.ColumnHeightOutput(unit.columnAddress()
			, columnHeightMap
		);
		
		TickOutput.WorldOutput world = new TickOutput.WorldOutput(cuboids
			, List.of(outputColumnHeight)
			, notYetReadyMutations
			, committedMutationCount
		);
		TickOutput.EntitiesOutput crowd = new TickOutput.EntitiesOutput(committedActionCount
			, processedEntities
		);
		TickOutput.CreaturesOutput creatures = new TickOutput.CreaturesOutput(false
			, updatedCreatures
		);
		TickOutput.PassivesOutput passives = new TickOutput.PassivesOutput(false
			, updatedPassives
		);
		return new TickOutput(world
			, crowd
			, creatures
			, passives
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, List.of()
			, Set.of()
			, Map.of()
		);
	}

	private TickMaterials _mergeTickStateAndWaitForNext(ProcessorElement elt
		, TickOutput perThreadData
		, long previousMillisInPreamble
		, long previousMillisPreambleEnd
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
			TickOutput masterFragment = _mergeAndClearPartialFragments(_partial);
			
			Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = _extractSnapshotBlockMutations(masterFragment);
			FlatResults flatResults = FlatResults.fromOutput(masterFragment);
			
			// Build the components of the snapshot (as part of the postamble time).
			Map<CuboidAddress, TickSnapshot.SnapshotCuboid> cuboids = _buildSnapshotCuboids(flatResults, snapshotBlockMutations);
			
			Map<Integer, List<ScheduledChange>> snapshotEntityMutations = new HashMap<>();
			Map<Integer, TickSnapshot.SnapshotEntity> entities = new HashMap<>();
			for (TargetedAction<ScheduledChange> targeted : masterFragment.newlyScheduledChanges())
			{
				_scheduleChangesForEntity(snapshotEntityMutations, targeted.targetId(), targeted.action());
			}
			for (TickOutput.EntityOutput ent : masterFragment.entities().entityOutput())
			{
				int id = ent.entityId();
				Assert.assertTrue(id > 0);
				Entity completed;
				Entity previousVersionOrNull;
				if (null != ent.updatedEntity())
				{
					// This means we changed.
					completed = ent.updatedEntity();
					previousVersionOrNull = ent.previousEntity();
				}
				else
				{
					// Unchanged, so return previous.
					completed = ent.previousEntity();
					previousVersionOrNull = null;
				}
				long commitLevel = flatResults.clientCommitLevelsById().get(id);
				
				// We want to schedule anything which wasn't yet ready.
				for (ScheduledChange change : ent.notYetReadyUnsortedActions())
				{
					_scheduleChangesForEntity(snapshotEntityMutations, id, change);
				}
				
				// Get the scheduled mutations (note that this is often null but we don't want to store null).
				List<ScheduledChange> scheduledMutations = snapshotEntityMutations.get(id);
				if (null == scheduledMutations)
				{
					scheduledMutations = List.of();
				}
				TickSnapshot.SnapshotEntity snapshot = new TickSnapshot.SnapshotEntity(
						completed
						, previousVersionOrNull
						, commitLevel
						, scheduledMutations
				);
				entities.put(id, snapshot);
			}
			
			Map<Integer, TickSnapshot.SnapshotCreature> creatures = _buildSnapshotCreatures(masterFragment);
			Map<Integer, TickSnapshot.SnapshotPassive> passives = _buildSnapshotPassives(masterFragment);
			
			// Collect the time stamps for stats.
			long endMillisPostamble = System.currentTimeMillis();
			long millisTickParallelPhase = (startMillisPostamble - previousMillisPreambleEnd);
			long millisTickPostamble = (endMillisPostamble - startMillisPostamble);
			
			// ***************** Tick ends here *********************
			
			// At this point, the tick to advance the world and crowd states has completed so publish the read-only results and wait before we put together the materials for the next tick.
			// Acknowledge that the tick is completed by creating a snapshot of the state.
			TickSnapshot.TickStats tickStats = new TickSnapshot.TickStats(_nextTick
				, previousMillisInPreamble
				, millisTickParallelPhase
				, millisTickPostamble
				, _threadStats.clone()
				, masterFragment.entities().committedMutationCount()
				, masterFragment.world().committedMutationCount()
			);
			
			TickSnapshot completedTick = new TickSnapshot(_nextTick
				, Collections.unmodifiableMap(cuboids)
				, Collections.unmodifiableMap(entities)
				, Collections.unmodifiableMap(creatures)
				, Collections.unmodifiableMap(passives)
				, flatResults.columnHeightMaps()
				, masterFragment.postedEvents()
				, masterFragment.internallyMarkedAlive()
				, tickStats
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
							long commitLevel = flatResults.clientCommitLevelsById().getOrDefault(id, 0L);
							newCommitLevels.put(id, commitLevel);
						}
					}
				}
				finally
				{
					_sharedDataLock.unlock();
				}
				
				// We now update our mutable collections for the materials to use in the next tick.
				Map<CuboidAddress, IReadOnlyCuboidData> mutableWorldState = new HashMap<>(flatResults.cuboidsByAddress());
				Map<CuboidAddress, CuboidHeightMap> nextTickMutableHeightMaps = new HashMap<>(flatResults.heightMapsByAddress());
				Map<Integer, Entity> mutableCrowdState = new HashMap<>(flatResults.entitiesById());
				Map<Integer, Long> mutableCommitLevels = new HashMap<>(flatResults.clientCommitLevelsById());
				Map<Integer, CreatureEntity> mutableCreatureState = new HashMap<>(flatResults.creaturesById());
				Map<Integer, PassiveEntity> mutablePassiveState = new HashMap<>(flatResults.passivesById());
				
				// Add any newly-loaded cuboids with their associated creatures and passives.
				Map<CuboidAddress, List<ScheduledMutation>> pendingMutations = new HashMap<>();
				Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutations = new HashMap<>(flatResults.periodicMutationsByCuboid());
				Set<CuboidAddress> cuboidsLoadedThisTick = new HashSet<>();
				if (null != newCuboids)
				{
					for (SuspendedCuboid<IReadOnlyCuboidData> suspended : newCuboids)
					{
						IReadOnlyCuboidData cuboid = suspended.cuboid();
						CuboidAddress address = cuboid.getCuboidAddress();
						
						cuboidsLoadedThisTick.add(address);
						
						Object old = mutableWorldState.put(address, cuboid);
						Assert.assertTrue(null == old);
						
						old = nextTickMutableHeightMaps.put(address, suspended.heightMap());
						Assert.assertTrue(null == old);
						
						// Load any creatures associated with this cuboid.
						for (CreatureEntity loadedCreature : suspended.creatures())
						{
							mutableCreatureState.put(loadedCreature.id(), loadedCreature);
						}
						
						// Load any passives associated with this cuboid.
						for (PassiveEntity loadedPassive : suspended.passives())
						{
							mutablePassiveState.put(loadedPassive.id(), loadedPassive);
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
				
				// Add newly-loaded player entities.
				Map<Integer, List<ScheduledChange>> nextTickChanges = new HashMap<>();
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
				for (Map.Entry<Integer, List<ScheduledChange>> entry : snapshotEntityMutations.entrySet())
				{
					// We can't modify the original so use a new container.
					int id = entry.getKey();
					for (ScheduledChange change : entry.getValue())
					{
						_scheduleChangesForEntity(nextTickChanges, id, change);
					}
				}
				for (Map.Entry<Integer, EntityActionSimpleMove<IMutablePlayerEntity>> container : newEntityChanges.entrySet())
				{
					// These are coming in from outside, so they should be run immediately (no delay for future), after anything already scheduled from the previous tick.
					ScheduledChange change = new ScheduledChange(container.getValue(), 0L);
					_scheduleChangesForEntity(nextTickChanges, container.getKey(), change);
				}
				
				// If there were any operator mutations, split them between client operator ID and specific entities.
				List<IEntityAction<IMutablePlayerEntity>> operatorChanges = new ArrayList<>();
				if (null != operatorMutations)
				{
					for (_OperatorMutationWrapper wrapper : operatorMutations)
					{
						// If the operator change isn't targeting the operator entity, schedule it on the specific player entity.
						if (EnginePlayers.OPERATOR_ENTITY_ID == wrapper.entityId)
						{
							operatorChanges.add(wrapper.mutation);
						}
						else
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
				
				// Update our map of latest client commit levels.
				mutableCommitLevels.putAll(newCommitLevels);
				
				// We can also extract any creature changes scheduled in the previous tick (creature actions are not saved in the cuboid so we only have what was scheduled in previous tick).
				Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> nextCreatureChanges = _scheduleNewCreatureActions(masterFragment);
				Map<Integer, List<IPassiveAction>> nextPassiveActions = _scheduleNewPassiveActions(masterFragment);
				
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
				
				// Carry forward the proxy cache from the previous tick unless the corresponding block locations changed or where removed.
				Set<AbsoluteLocation> changedBlocksInPreviousTick = flatResults.allChangedBlockLocations();
				Map<AbsoluteLocation, BlockProxy> previousProxyCache = masterFragment.populatedProxyCache().entrySet().stream()
					.filter((Map.Entry<AbsoluteLocation, BlockProxy> ent) -> {
						AbsoluteLocation location = ent.getKey();
						boolean shouldDrop = changedBlocksInPreviousTick.contains(location);
						if (!shouldDrop && (null != cuboidsToDrop))
						{
							CuboidAddress cuboidAddress = location.getCuboidAddress();
							shouldDrop = cuboidsToDrop.contains(cuboidAddress);
						}
						return !shouldDrop;
					})
					.collect(Collectors.toMap((Map.Entry<AbsoluteLocation, BlockProxy> ent) -> ent.getKey(), (Map.Entry<AbsoluteLocation, BlockProxy> ent) -> ent.getValue()))
				;
				
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
				Map<Integer, TickInput.EntityInput> changesToRun = _determineChangesToRun(mutableCrowdState, nextTickChanges, mutableCommitLevels);
				
				// WARNING:  completedHeightMaps does NOT include the new height maps loaded after the previous tick finished!
				// (this is done to avoid the cost of rebuilding the maps since the column height maps are not guaranteed to be fully accurate)
				EntityCollection entityCollection = EntityCollection.fromMaps(mutableCrowdState, mutableCreatureState);
				TickInput highLevelPlan = _packageHighLevelWorkUnits(mutableWorldState
					, nextTickMutableHeightMaps
					, mutableCrowdState
					, mutableCommitLevels
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
						, flatResults.columnHeightMaps()
						, Collections.unmodifiableMap(mutableCrowdState)
						// completedCreatures
						, Collections.unmodifiableMap(mutableCreatureState)
						, Collections.unmodifiableMap(mutablePassiveState)
						
						, Collections.unmodifiableList(operatorChanges)
						, flatResults.blockUpdatesByCuboid()
						, flatResults.lightingUpdatesByCuboid()
						, flatResults.logicUpdatesByCuboid()
						, Collections.unmodifiableSet(cuboidsLoadedThisTick)
						, previousProxyCache
						
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

	private static Map<CuboidAddress, List<ScheduledMutation>> _extractSnapshotBlockMutations(TickOutput masterFragment)
	{
		Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations = new HashMap<>();
		for (ScheduledMutation scheduledMutation : masterFragment.newlyScheduledMutations())
		{
			_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
		}
		for (ScheduledMutation scheduledMutation : masterFragment.world().notYetReadyMutations())
		{
			_scheduleMutationForCuboid(snapshotBlockMutations, scheduledMutation);
		}
		return snapshotBlockMutations;
	}

	private static Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> _scheduleNewCreatureActions(TickOutput masterFragment)
	{
		Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> nextCreatureChanges = new HashMap<>();
		for (TargetedAction<IEntityAction<IMutableCreatureEntity>> targeted : masterFragment.newlyScheduledCreatureChanges())
		{
			_scheduleChangesForEntity(nextCreatureChanges, targeted.targetId(), targeted.action());
		}
		return nextCreatureChanges;
	}

	private static Map<Integer, List<IPassiveAction>> _scheduleNewPassiveActions(TickOutput masterFragment)
	{
		Map<Integer, List<IPassiveAction>> nextCreatureChanges = new HashMap<>();
		for (TargetedAction<IPassiveAction> targeted : masterFragment.newlyScheduledPassiveActions())
		{
			_scheduleChangesForEntity(nextCreatureChanges, targeted.targetId(), targeted.action());
		}
		return nextCreatureChanges;
	}

	private static Map<Integer, TickInput.EntityInput> _determineChangesToRun(Map<Integer, Entity> mutableCrowdState
		, Map<Integer, List<ScheduledChange>> nextTickChanges
		, Map<Integer, Long> clientCommitLevelsById
	)
	{
		Map<Integer, TickInput.EntityInput> changesToRun = new HashMap<>();
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
				long commitLevel = clientCommitLevelsById.get(id);
				TickInput.EntityInput input = new TickInput.EntityInput(entity
					, Collections.unmodifiableList(list)
					, commitLevel
				);
				changesToRun.put(id, input);
			}
			else
			{
				System.out.println("WARNING: missing entity " + id);
			}
		}
		return changesToRun;
	}

	private synchronized void _acknowledgeTickCompleteAndWaitForNext(TickSnapshot newSnapshot)
	{
		_snapshot = newSnapshot;
		this.notifyAll();
		while (_snapshot.tickNumber() == _nextTick)
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

	private static <T> void _scheduleChangesForEntity(Map<Integer, List<T>> nextTickChanges, int entityId, T action)
	{
		List<T> queue = nextTickChanges.get(entityId);
		if (null == queue)
		{
			// We want to build this as mutable.
			queue = new LinkedList<>();
			nextTickChanges.put(entityId, queue);
		}
		queue.add(action);
	}

	private TickSnapshot _locked_waitForTickComplete()
	{
		while (_snapshot.tickNumber() != _nextTick)
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

	private static TickOutput _mergeAndClearPartialFragments(TickOutput[] partials)
	{
		// EngineCuboids.ProcessedFragment world
		List<TickOutput.CuboidOutput> cuboids = new ArrayList<>();
		List<TickOutput.ColumnHeightOutput> columns = new ArrayList<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		int world_committedMutationCount = 0;
		
		// EnginePlayers.ProcessedGroup crowd
		int players_committedMutationCount = 0;
		List<TickOutput.EntityOutput> entityOutput = new ArrayList<>();
		
		// EngineCreatures.CreatureGroup creatures
		List<TickOutput.BasicOutput<CreatureEntity>> creatureOutput = new ArrayList<>();
		
		// EnginePassives
		List<TickOutput.BasicOutput<PassiveEntity>> passiveOutput = new ArrayList<>();
		
		List<CreatureEntity> spawnedCreatures = new ArrayList<>();
		List<PassiveEntity> spawnedPassives = new ArrayList<>();
		List<ScheduledMutation> newlyScheduledMutations = new ArrayList<>();
		List<TargetedAction<ScheduledChange>> newlyScheduledChanges = new ArrayList<>();
		List<TargetedAction<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges = new ArrayList<>();
		List<TargetedAction<IPassiveAction>> newlyScheduledPassiveActions = new ArrayList<>();
		List<EventRecord> postedEvents = new ArrayList<>();
		Set<CuboidAddress> internallyMarkedAlive = new HashSet<>();
		Map<AbsoluteLocation, BlockProxy> populatedProxyCache = new HashMap<>();
		
		for (int i = 0; i < partials.length; ++i)
		{
			TickOutput fragment = partials[i];
			
			// EngineCuboids.ProcessedFragment world
			cuboids.addAll(fragment.world().cuboids());
			columns.addAll(fragment.world().columns());
			notYetReadyMutations.addAll(fragment.world().notYetReadyMutations());
			world_committedMutationCount += fragment.world().committedMutationCount();
			
			// EnginePlayers.ProcessedGroup crowd
			players_committedMutationCount += fragment.entities().committedMutationCount();
			entityOutput.addAll(fragment.entities().entityOutput());
			
			// EngineCreatures.CreatureGroup creatures
			creatureOutput.addAll(fragment.creatures().creatureOutput());
			
			// EnginePassives
			passiveOutput.addAll(fragment.passives().passiveOutput());
			
			spawnedCreatures.addAll(fragment.spawnedCreatures());
			spawnedPassives.addAll(fragment.spawnedPassives());
			newlyScheduledMutations.addAll(fragment.newlyScheduledMutations());
			newlyScheduledChanges.addAll(fragment.newlyScheduledChanges());
			newlyScheduledCreatureChanges.addAll(fragment.newlyScheduledCreatureChanges());
			newlyScheduledPassiveActions.addAll(fragment.newlyScheduledPassiveActions());
			postedEvents.addAll(fragment.postedEvents());
			internallyMarkedAlive.addAll(fragment.internallyMarkedAlive());
			populatedProxyCache.putAll(fragment.populatedProxyCache());
			partials[i] = null;
		}
		
		TickOutput.WorldOutput world = new TickOutput.WorldOutput(cuboids
			, columns
			, notYetReadyMutations
			, world_committedMutationCount
		);
		TickOutput.EntitiesOutput crowd = new TickOutput.EntitiesOutput(players_committedMutationCount
			, entityOutput
		);
		TickOutput.CreaturesOutput creatures = new TickOutput.CreaturesOutput(false
			, creatureOutput
		);
		TickOutput.PassivesOutput passives = new TickOutput.PassivesOutput(false
			, passiveOutput
		);
		return new TickOutput(world
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
			, populatedProxyCache
		);
	}

	private static Map<CuboidAddress, TickSnapshot.SnapshotCuboid> _buildSnapshotCuboids(FlatResults flatResults
		, Map<CuboidAddress, List<ScheduledMutation>> snapshotBlockMutations
	)
	{
		Map<CuboidAddress, TickSnapshot.SnapshotCuboid> cuboids = new HashMap<>();
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> ent : flatResults.cuboidsByAddress().entrySet())
		{
			CuboidAddress key = ent.getKey();
			IReadOnlyCuboidData cuboid = ent.getValue();
			
			// The list of block changes will be null if nothing changed but the list of mutations will never be null, although typically empty.
			List<MutationBlockSetBlock> changedBlocks = flatResults.resultantBlockChangesByCuboid().get(key);
			Assert.assertTrue((null == changedBlocks) || !changedBlocks.isEmpty());
			List<ScheduledMutation> scheduledMutations = snapshotBlockMutations.get(key);
			if (null == scheduledMutations)
			{
				scheduledMutations = List.of();
			}
			Map<BlockAddress, Long> periodicMutationMillis = flatResults.periodicMutationsByCuboid().get(key);
			if (null == periodicMutationMillis)
			{
				periodicMutationMillis = Map.of();
			}
			TickSnapshot.SnapshotCuboid snapshot = new TickSnapshot.SnapshotCuboid(
					cuboid
					, changedBlocks
					, scheduledMutations
					, periodicMutationMillis
			);
			cuboids.put(key, snapshot);
		}
		return cuboids;
	}

	private static Map<Integer, TickSnapshot.SnapshotCreature> _buildSnapshotCreatures(TickOutput masterFragment)
	{
		Map<Integer, TickSnapshot.SnapshotCreature> creatures = new HashMap<>();
		
		// Carry over whatever is still alive.
		for (TickOutput.BasicOutput<CreatureEntity> ent : masterFragment.creatures().creatureOutput())
		{
			int id = ent.id();
			Assert.assertTrue(id < 0);
			
			if (ent.didDie())
			{
				// This died so we don't want to report it in the snapshot.
			}
			else
			{
				CreatureEntity completed;
				CreatureEntity previousVersionOrNull;
				if (null != ent.updated())
				{
					// This means we changed.
					completed = ent.updated();
					previousVersionOrNull = ent.previous();
				}
				else
				{
					// Unchanged, so return previous.
					completed = ent.previous();
					previousVersionOrNull = null;
				}
				
				TickSnapshot.SnapshotCreature snapshot = new TickSnapshot.SnapshotCreature(
					completed
					, previousVersionOrNull
				);
				creatures.put(id, snapshot);
			}
		}
		
		// Include new spawns.
		for (CreatureEntity newCreature : masterFragment.spawnedCreatures())
		{
			int id = newCreature.id();
			Assert.assertTrue(id < 0);
			
			TickSnapshot.SnapshotCreature snapshot = new TickSnapshot.SnapshotCreature(
				newCreature
				, null
			);
			creatures.put(id, snapshot);
		}
		return creatures;
	}

	private static Map<Integer, TickSnapshot.SnapshotPassive> _buildSnapshotPassives(TickOutput masterFragment)
	{
		Map<Integer, TickSnapshot.SnapshotPassive> passives = new HashMap<>();
		
		// Carry over whatever is still alive.
		for (TickOutput.BasicOutput<PassiveEntity> ent : masterFragment.passives().passiveOutput())
		{
			int id = ent.id();
			Assert.assertTrue(id > 0);
			
			if (ent.didDie())
			{
				// This died so we don't want to report it in the snapshot.
			}
			else
			{
				PassiveEntity completed;
				PassiveEntity previousVersionOrNull;
				if (null != ent.updated())
				{
					// This means we changed.
					completed = ent.updated();
					previousVersionOrNull = ent.previous();
				}
				else
				{
					// Unchanged, so return previous.
					completed = ent.previous();
					previousVersionOrNull = null;
				}
				
				TickSnapshot.SnapshotPassive snapshot = new TickSnapshot.SnapshotPassive(
					completed
					, previousVersionOrNull
				);
				passives.put(id, snapshot);
			}
		}
		
		// Include new spawns.
		for (PassiveEntity newPassive : masterFragment.spawnedPassives())
		{
			int id = newPassive.id();
			Assert.assertTrue(id > 0);
			
			TickSnapshot.SnapshotPassive snapshot = new TickSnapshot.SnapshotPassive(
				newPassive
				, null
			);
			passives.put(id, snapshot);
		}
		return passives;
	}

	private static TickInput _packageHighLevelWorkUnits(Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids
		, Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps
		, Map<Integer, Entity> completedEntities
		, Map<Integer, Long> clientCommitLevelsById
		, Map<Integer, TickInput.EntityInput> entitiesWithWork
		, Map<Integer, CreatureEntity> completedCreatures
		, Map<Integer, PassiveEntity> completedPassives
		, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
		, Map<CuboidAddress, Map<BlockAddress, Long>> periodicMutationMillis
		, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> creatureChanges
		, Map<Integer, List<IPassiveAction>> passiveActions
	)
	{
		List<TickInput.EntityInput> entitiesInUnloadedCuboids = new ArrayList<>();
		Map<CuboidAddress, List<TickInput.EntityInput>> workingEntityList = new HashMap<>();
		for (Entity entity : completedEntities.values())
		{
			// We want to see a work unit for every entity, so that the entire system is captured in the plan.
			int id = entity.id();
			TickInput.EntityInput workUnit = entitiesWithWork.get(id);
			if (null == workUnit)
			{
				long commitLevel = clientCommitLevelsById.get(id);
				workUnit = new TickInput.EntityInput(entity
					, List.of()
					, commitLevel
				);
			}
			CuboidAddress thisAddress = entity.location().getBlockLocation().getCuboidAddress();
			if (completedCuboids.containsKey(thisAddress))
			{
				if (!workingEntityList.containsKey(thisAddress))
				{
					workingEntityList.put(thisAddress, new ArrayList<>());
				}
				List<TickInput.EntityInput> list = workingEntityList.get(thisAddress);
				list.add(workUnit);
			}
			else
			{
				// This cuboid isn't loaded so spill it to the next tick.
				// This is usually due to load ordering (an entity might be created before its cuboid is loaded - thus dropping the creation of its periodic mutation).
				entitiesInUnloadedCuboids.add(workUnit);
			}
		}
		Map<CuboidAddress, List<TickInput.CreatureInput>> workingCreatureList = new HashMap<>();
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
			TickInput.CreatureInput unit = new TickInput.CreatureInput(entity
				, actions
			);
			List<TickInput.CreatureInput> list = workingCreatureList.get(thisAddress);
			list.add(unit);
		}
		Map<CuboidAddress, List<TickInput.PassiveInput>> workingPassiveList = new HashMap<>();
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
			TickInput.PassiveInput unit = new TickInput.PassiveInput(entity
				, actions
			);
			List<TickInput.PassiveInput> list = workingPassiveList.get(thisAddress);
			list.add(unit);
		}
		
		Map<CuboidColumnAddress, List<TickInput.CuboidInput>> workingWorkList = new HashMap<>();
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
			List<TickInput.EntityInput> entityList = workingEntityList.get(address);
			if (null == entityList)
			{
				entityList = List.of();
			}
			List<TickInput.CreatureInput> creatures = workingCreatureList.get(address);
			if (null == creatures)
			{
				creatures = List.of();
			}
			List<TickInput.PassiveInput> passives = workingPassiveList.get(address);
			if (null == passives)
			{
				passives = List.of();
			}
			TickInput.CuboidInput unit = new TickInput.CuboidInput(cuboid
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
			List<TickInput.CuboidInput> list = workingWorkList.get(column);
			list.add(unit);
		}
		
		List<TickInput.ColumnInput> result = new ArrayList<>();
		for (CuboidColumnAddress column : workingWorkList.keySet())
		{
			List<TickInput.CuboidInput> list = workingWorkList.get(column);
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
				(TickInput.CuboidInput inner) -> 1 + inner.mutations().size() + inner.creatures().size() + inner.entities().size()
			).sum();
			TickInput.ColumnInput unit = new TickInput.ColumnInput(column
				, Collections.unmodifiableList(list)
				, priorityHint
			);
			result.add(unit);
		}
		// Now sort by priority list (descending on priorityHint).
		result.sort((TickInput.ColumnInput one, TickInput.ColumnInput two) -> two.priorityHint() - one.priorityHint());
		return new TickInput(Collections.unmodifiableList(result)
			, Collections.unmodifiableList(entitiesInUnloadedCuboids)
		);
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
			, Map<AbsoluteLocation, BlockProxy> previousProxyCache
			
			// Higher-level data associated with the materials.
			, EntityCollection entityCollection
			, TickInput highLevel
			
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
}
