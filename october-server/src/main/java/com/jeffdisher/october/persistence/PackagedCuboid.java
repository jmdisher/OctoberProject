package com.jeffdisher.october.persistence;

import java.util.List;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.CreatureEntity;


/**
 * Similar to SuspendedCuboid but intended for the writing path since less information is required.
 */
public record PackagedCuboid(IReadOnlyCuboidData cuboid
		, List<CreatureEntity> creatures
		, List<ScheduledMutation> mutations
)
{
}
