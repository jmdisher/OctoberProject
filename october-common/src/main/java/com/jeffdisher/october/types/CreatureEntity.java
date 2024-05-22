package com.jeffdisher.october.types;

import java.util.List;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity which represents animals or monsters.
 * These are fairly minimal but can have internal state for path-finding, etc.
 * Additionally, some things which can vary by player (volume, for example), are fixed by type for creatures.
 */
public record CreatureEntity(int id
		, EntityType type
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// We track the current z-velocity in blocks per second, up.
		, float zVelocityPerSecond
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// These data elements are considered ephemeral and will NOT be persisted.
		// The last tick where an action was taken (used to determine when the creature has "idled" long enough before next move).
		, long lastActionGameTick
		// The next steps required to get to the next step in movementPlan.
		, List<IMutationEntity<IMutableMinimalEntity>> stepsToNextMove
		// The sequence of locations where this creature plans to go.
		, List<AbsoluteLocation> movementPlan
)
{
	// Volume constants for different creature types.
	public static final EntityVolume VOLUME_COW = new EntityVolume(0.7f, 0.8f);

	public EntityVolume getVolume()
	{
		EntityVolume volume;
		switch (type)
		{
		case COW:
			volume = VOLUME_COW;
			break;
		default:
			throw Assert.unreachable();
		}
		return volume;
	}
}
