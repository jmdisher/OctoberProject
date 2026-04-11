package com.jeffdisher.october.ticks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TargetedAction;


/**
 * Data types which are the output results of the parallel tick execution engine.
 * One of these top-level instances is produced for each thread in the parallel engine.
 * Note that this does NOT correspond directly to the shape of TickInput, as this has some additional data and uses a
 * far flatter orientation.
 */
public record TickOutput(WorldOutput world
	, EntitiesOutput entities
	, CreaturesOutput creatures
	, PassivesOutput passives
	, List<CreatureEntity> spawnedCreatures
	, List<PassiveEntity> spawnedPassives
	, List<ScheduledMutation> newlyScheduledMutations
	, List<TargetedAction<ScheduledChange>> newlyScheduledChanges
	, List<TargetedAction<IEntityAction<IMutableCreatureEntity>>> newlyScheduledCreatureChanges
	, List<TargetedAction<IPassiveAction>> newlyScheduledPassiveActions
	, List<EventRecord> postedEvents
	, Set<CuboidAddress> internallyMarkedAlive
	, Map<AbsoluteLocation, BlockProxy> populatedProxyCache
)
{
	public static record WorldOutput(List<CuboidOutput> cuboids
		, List<ColumnHeightOutput> columns
		, List<ScheduledMutation> notYetReadyMutations
		, int committedMutationCount
	)
	{
		public static WorldOutput empty()
		{
			return new WorldOutput(List.of(), List.of(), List.of(), 0);
		}
	}

	public static record EntitiesOutput(int committedMutationCount
		, List<EntityOutput> entityOutput
	)
	{
		public static EntitiesOutput empty()
		{
			return new EntitiesOutput(0, List.of());
		}
	}

	public static record CreaturesOutput(boolean ignored
		, List<BasicOutput<CreatureEntity>> creatureOutput
	)
	{
		public static CreaturesOutput empty()
		{
			return new TickOutput.CreaturesOutput(false, List.of());
		}
	}

	public static record PassivesOutput(boolean ignored
		, List<BasicOutput<PassiveEntity>> passiveOutput
	)
	{
		public static PassivesOutput empty()
		{
			return new TickOutput.PassivesOutput(false, List.of());
		}
	}

	public static record BasicOutput<T>(int id
		, T previous
		// The updated will be null if it didn't change or died.
		, T updated
		, boolean didDie
	) {}

	public static record EntityOutput(int entityId
		// The entity from the previous tick.
		, Entity previousEntity
		// The updated entity from this tick (null if not changed).
		, Entity updatedEntity
		// The changes which were not ready to run in this tick (could be empty but never null).
		, List<ScheduledChange> notYetReadyUnsortedActions
		, long clientCommitLevel
	) {}

	public static record CuboidOutput(CuboidAddress address
		, IReadOnlyCuboidData previousCuboid
		, IReadOnlyCuboidData updatedCuboidOrNull
		, CuboidHeightMap previousHeightMap
		, CuboidHeightMap updatedHeightMapOrNull
		, Map<BlockAddress, Long> periodicNotReadyMutations
		, List<BlockChangeDescription> blockChanges
	) {}

	public static record ColumnHeightOutput(CuboidColumnAddress columnAddress
		, ColumnHeightMap columnHeightMap
	) {}

	public static TickOutput empty()
	{
		return new TickOutput(TickOutput.WorldOutput.empty()
			, TickOutput.EntitiesOutput.empty()
			, TickOutput.CreaturesOutput.empty()
			, TickOutput.PassivesOutput.empty()
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

	public static TickOutput mergeAndClearPartialFragments(TickOutput[] partials)
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
}
