package com.jeffdisher.october.types;

import java.util.List;

import com.jeffdisher.october.mutations.IMutationEntity;


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
		, List<IMutationEntity<IMutableCreatureEntity>> stepsToNextMove
		// The sequence of locations where this creature plans to go.
		, List<AbsoluteLocation> movementPlan
		// This data field is defined by helpers based on the type (remember that it is NOT persistent).
		, Object extendedData
)
{
}
