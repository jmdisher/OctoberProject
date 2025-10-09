package com.jeffdisher.october.persistence;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.PassiveEntity;


/**
 * Similar to SuspendedCuboid but intended for the writing path since less information is required.
 */
public record PackagedCuboid(IReadOnlyCuboidData cuboid
		, List<CreatureEntity> creatures
		, List<ScheduledMutation> pendingMutations
		, Map<BlockAddress, Long> periodicMutationMillis
		, List<PassiveEntity> passives
)
{
}
