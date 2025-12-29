package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.engine.EnginePlayers;
import com.jeffdisher.october.engine.EngineCuboids;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;


/**
 * A helper class which exposes a helper just to run a single entity change and one step of follow-up block mutations.
 */
public class OneOffRunner
{
	/**
	 * Runs a single entity change on the given description of state, returning the updated version of the state (null
	 * if the entity change failed).
	 * Note that a single iteration of follow-up block change mutations will also be run.
	 * 
	 * @param state The state before the change.
	 * @param eventSink The sink where events will be delivered.
	 * @param millisPerTick How many milliseconds are in a tick.
	 * @param currentTickTimeMillis The current time, in milliseconds.
	 * @param mutation The entity change to run.
	 * @return The state after the change, or null if the entity change failed.
	 */
	public static StatePackage runOneChange(StatePackage state
			, TickProcessingContext.IEventSink eventSink
			, long millisPerTick
			, long currentTickTimeMillis 
			, IEntityAction<IMutablePlayerEntity> mutation
	)
	{
		// Setup components.
		Set<CuboidAddress> loadedCuboids = state.world.keySet();
		Set<Integer> allPlayerIds = new HashSet<>(state.otherEntities.keySet().stream().filter((Integer i) -> i > 0).toList());
		allPlayerIds.add(state.thisEntity.id());
		Set<Integer> allCreatureIds = state.otherEntities.keySet().stream().filter((Integer i) -> i < 0).collect(Collectors.toSet());
		Set<Integer> allPassiveIds = state.passives.keySet();
		CommonMutationSink newMutationSink = new CommonMutationSink(loadedCuboids);
		CommonChangeSink newChangeSink = new CommonChangeSink(allPlayerIds, allCreatureIds, allPassiveIds);
		TickProcessingContext context = _createContext(state, newMutationSink, newChangeSink, eventSink, millisPerTick, currentTickTimeMillis);
		
		// Run initial change.
		ScheduledChange scheduled = new ScheduledChange(mutation, 0L);
		EnginePlayers.SinglePlayerResult playerResult = EnginePlayers.processOnePlayer(context
			, EntityCollection.emptyCollection()
			, state.thisEntity()
			, List.of(scheduled)
		);
		boolean wasSuccess = (playerResult.committedMutationCount() > 0);
		Entity updatedEntity = wasSuccess
				? playerResult.changedEntityOrNull()
				: null
		;
		List<ScheduledMutation> immediateMutations = newMutationSink.takeExportedMutations().stream()
				.filter((ScheduledMutation input) -> (0L == input.millisUntilReady()))
				.toList()
		;
		
		// Run follow-ups against blocks.
		Map<CuboidAddress, IReadOnlyCuboidData> changedCuboids = Map.of();
		Map<CuboidAddress, CuboidHeightMap> heightFragment = Map.of();
		Map<CuboidAddress, List<BlockChangeDescription>> optionalBlockChanges = Map.of();
		if (wasSuccess && !immediateMutations.isEmpty())
		{
			Map<CuboidAddress, List<ScheduledMutation>> splitMutations = new HashMap<>();
			for (ScheduledMutation imm : immediateMutations)
			{
				CuboidAddress address = imm.mutation().getAbsoluteLocation().getCuboidAddress();
				if (!splitMutations.containsKey(address))
				{
					splitMutations.put(address, new ArrayList<>());
				}
				splitMutations.get(address).add(imm);
			}
			// Note that we will need to capture output mutations from these sinks if there is an interest in running multiple ticks in advance here.
			CommonChangeSink innerChangeSink = new CommonChangeSink(allPlayerIds, allCreatureIds, allPassiveIds);
			TickProcessingContext innerContext = _createContext(state, new CommonMutationSink(loadedCuboids), innerChangeSink, eventSink, millisPerTick, currentTickTimeMillis);
			changedCuboids = new HashMap<>();
			heightFragment = new HashMap<>();
			optionalBlockChanges = new HashMap<>();
			for (Map.Entry<CuboidAddress, List<ScheduledMutation>> mut : splitMutations.entrySet())
			{
				CuboidAddress key = mut.getKey();
				List<ScheduledMutation> list = mut.getValue();
				EngineCuboids.SingleCuboidResult result = EngineCuboids.processOneCuboid(innerContext
					, state.world.keySet()
					, list
					, Map.of()
					, Map.of()
					, Map.of()
					, Map.of()
					, Set.of()
					, key
					, state.world.get(key)
				);
				if (null != result.changedCuboidOrNull())
				{
					changedCuboids.put(key, result.changedCuboidOrNull());
				}
				if (null != result.changedHeightMap())
				{
					heightFragment.put(key, result.changedHeightMap());
				}
				if ((null != result.changedBlocks()) && !result.changedBlocks().isEmpty())
				{
					optionalBlockChanges.put(key, result.changedBlocks());
				}
			}
		}
		
		// Repackage results.
		StatePackage updatedState = null;
		if (wasSuccess)
		{
			Map<CuboidAddress, IReadOnlyCuboidData> initialCuboids = new HashMap<>(state.world);
			Map<CuboidAddress, CuboidHeightMap> initialHeights = new HashMap<>(state.heights);
			Map<Integer, PartialEntity> initialCrowd = new HashMap<>(state.otherEntities);
			Map<Integer, PartialPassive> initialPassives = new HashMap<>(state.passives);
			initialCuboids.putAll(changedCuboids);
			initialHeights.putAll(heightFragment);
			updatedState = new StatePackage(updatedEntity, initialCuboids, initialHeights, optionalBlockChanges, initialCrowd, initialPassives);
		}
		return updatedState;
	}


	private static TickProcessingContext _createContext(StatePackage state
			, CommonMutationSink newMutationSink
			, CommonChangeSink newChangeSink
			, TickProcessingContext.IEventSink eventSink
			, long millisPerTick
			, long currentTickTimeMillis 
	)
	{
		long gameTick = 0L;
		LazyLocationCache<BlockProxy> cachingLoader = new LazyLocationCache<>((AbsoluteLocation location) -> {
			IReadOnlyCuboidData cuboid = state.world.get(location.getCuboidAddress());
			return (null != cuboid)
				? new BlockProxy(location.getBlockAddress(), cuboid)
				: null
			;
		});
		int thisEntityId = state.thisEntity().id();
		TickProcessingContext.IPassiveSearch passiveSearch = new TickProcessingContext.IPassiveSearch() {
			@Override
			public PartialPassive getById(int id)
			{
				return state.passives.get(id);
			}
			@Override
			public PartialPassive[] findPassiveItemSlotsInRegion(EntityLocation base, EntityLocation edge)
			{
				// We don't do passive processing on the client.
				return new PartialPassive[0];
			}
		};
		TickProcessingContext context = new TickProcessingContext(gameTick
				, cachingLoader
				, (Integer entityId) -> (thisEntityId == entityId)
					? MinimalEntity.fromEntity(state.thisEntity())
					: MinimalEntity.fromPartialEntity(state.otherEntities.get(entityId))
				, passiveSearch
				, null
				, newMutationSink
				, newChangeSink
				// We never spawn creatures on the client so no ID assigner.
				, null
				, (PassiveType type, EntityLocation location, EntityLocation velocity, Object extendedData) -> {
					// We might try to spawn passives here but they should just be ignored in one-off (since this isn't authoritative).
				}
				// We can't provide random numbers on the client so set this to null and the consumer will handle this as a degenerate case.
				, null
				, eventSink
				, (CuboidAddress address) -> {}
				// By default, we run in hostile mode.
				, new WorldConfig()
				, millisPerTick
				, currentTickTimeMillis
		);
		return context;
	}


	/**
	 * A packaged read-only state.
	 * Note that, when output, this will only include what changed (which could mean it is completely empty).
	 */
	public static record StatePackage(Entity thisEntity
		, Map<CuboidAddress, IReadOnlyCuboidData> world
		, Map<CuboidAddress, CuboidHeightMap> heights
		, Map<CuboidAddress, List<BlockChangeDescription>> optionalBlockChanges
		, Map<Integer, PartialEntity> otherEntities
		, Map<Integer, PartialPassive> passives
	) {}
}
