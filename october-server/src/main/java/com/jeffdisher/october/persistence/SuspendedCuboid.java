package com.jeffdisher.october.persistence;

import java.util.List;

import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.CreatureEntity;


/**
 * Just a container of cuboids and associated creatures and mutations used in the persistence layer.
 * This type exists just to tie these together for convenience through a few parts of the system.
 */
public record SuspendedCuboid<T>(T cuboid
		, CuboidHeightMap heightMap
		, List<CreatureEntity> creatures
		, List<ScheduledMutation> mutations
)
{
}
