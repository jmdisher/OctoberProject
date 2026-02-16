package com.jeffdisher.october.ticks;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;


/**
 * Data types which are the input work units to the parallel tick execution engine.
 * These are packaged along-side other data describing the environment for tick execution.
 * While there are many valid strategies for splitting threads across these work units, the current strategy is to give
 * each Column to a thread and handle any entities in not-yet-loaded cuboids in a single thread.
 */
public record TickInput(List<ColumnInput> columns
	// When players have joined, but their underlying cuboids haven't yet loaded, we skip processing them.
	, List<EntityInput> entitiesInUnloadedCuboids
)
{
	public static record ColumnInput(CuboidColumnAddress columnAddress
		, List<CuboidInput> cuboids
		, int priorityHint
	) {}

	public static record CuboidInput(IReadOnlyCuboidData cuboid
		, CuboidHeightMap cuboidHeightMap
		, List<ScheduledMutation> mutations
		, Map<BlockAddress, Long> periodicMutationMillis
		, List<EntityInput> entities
		, List<CreatureInput> creatures
		, List<PassiveInput> passives
	) {}

	public static record EntityInput(Entity entity
		, List<ScheduledChange> unsortedActions
	) {}

	public static record CreatureInput(CreatureEntity creature
		, List<IEntityAction<IMutableCreatureEntity>> actions
	) {}

	public static record PassiveInput(PassiveEntity passive
		, List<IPassiveAction> actions
	) {}
}
