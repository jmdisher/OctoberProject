package com.jeffdisher.october.persistence;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.PassiveEntity;


/**
 * Just a container of cuboids and associated creatures and mutations used in the persistence layer.
 * This type exists just to tie these together for convenience through a few parts of the system.
 */
public record SuspendedCuboid<T>(T cuboid
		, CuboidHeightMap heightMap
		, List<CreatureEntity> creatures
		, List<ScheduledMutation> pendingMutations
		, Map<BlockAddress, Long> periodicMutationMillis
		, List<PassiveEntity> passives
)
{
}
