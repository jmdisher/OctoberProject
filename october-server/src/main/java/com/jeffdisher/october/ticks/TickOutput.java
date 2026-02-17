package com.jeffdisher.october.ticks;

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
	) {}

	public static record EntitiesOutput(int committedMutationCount
		, List<EntityOutput> entityOutput
	) {}

	public static record CreaturesOutput(boolean ignored
		, List<BasicOutput<CreatureEntity>> creatureOutput
	) {}

	public static record PassivesOutput(boolean ignored
		, List<BasicOutput<PassiveEntity>> passiveOutput
	) {}

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
}
