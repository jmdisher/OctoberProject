package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.CommonChangeSink;
import com.jeffdisher.october.logic.CommonMutationSink;
import com.jeffdisher.october.logic.CrowdProcessor;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.logic.SyncPoint;
import com.jeffdisher.october.logic.WorldProcessor;
import com.jeffdisher.october.mutations.IEntityAction;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialEntity;
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
		ProcessorElement singleThreadElement = new ProcessorElement(0, new SyncPoint(1), new AtomicInteger(0));
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		TickProcessingContext context = _createContext(state, newMutationSink, newChangeSink, eventSink, millisPerTick, currentTickTimeMillis);
		
		// Run initial change.
		int thisEntityId = state.thisEntity().id();
		ScheduledChange scheduled = new ScheduledChange(mutation, 0L);
		CrowdProcessor.ProcessedGroup innerGroup = CrowdProcessor.processCrowdGroupParallel(singleThreadElement
				, Map.of(thisEntityId, state.thisEntity())
				, context
				, Map.of(thisEntityId, List.of(scheduled))
		);
		boolean wasSuccess = (innerGroup.committedMutationCount() > 0);
		Entity updatedEntity = wasSuccess
				? innerGroup.updatedEntities().get(thisEntityId)
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
			TickProcessingContext innerContext = _createContext(state, new CommonMutationSink(), new CommonChangeSink(), eventSink, millisPerTick, currentTickTimeMillis);
			WorldProcessor.ProcessedFragment innerFragment = WorldProcessor.processWorldFragmentParallel(singleThreadElement
					, state.world
					, innerContext
					, splitMutations
					, Map.of()
					, Map.of()
					, Map.of()
					, Map.of()
					, Set.of()
			);
			changedCuboids = innerFragment.stateFragment();
			heightFragment = innerFragment.heightFragment();
			optionalBlockChanges = innerFragment.blockChangesByCuboid();
		}
		
		// Repackage results.
		StatePackage updatedState = null;
		if (wasSuccess)
		{
			Map<CuboidAddress, IReadOnlyCuboidData> initialCuboids = new HashMap<>(state.world);
			Map<CuboidAddress, CuboidHeightMap> initialHeights = new HashMap<>(state.heights);
			Map<Integer, PartialEntity> initialCrowd = new HashMap<>(state.otherEntities);
			initialCuboids.putAll(changedCuboids);
			initialHeights.putAll(heightFragment);
			updatedState = new StatePackage(updatedEntity, initialCuboids, initialHeights, optionalBlockChanges, initialCrowd);
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
		TickProcessingContext context = new TickProcessingContext(gameTick
				, cachingLoader
				, (Integer entityId) -> (thisEntityId == entityId)
					? MinimalEntity.fromEntity(state.thisEntity())
					: MinimalEntity.fromPartialEntity(state.otherEntities.get(entityId))
				, null
				, newMutationSink
				, newChangeSink
				// We never spawn creatures on the client so no ID assigner.
				, null
				// We need a random number generator for a few cases (like attack) but the server will send us the authoritative result.
				, (int bound) -> 0
				, eventSink
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
	) {}
}
