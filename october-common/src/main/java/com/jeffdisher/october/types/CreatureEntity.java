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
		// The next steps required to get to the next block.  This is normally null but may have something in it if the
		// entity is walking slowly and requires multiple ticks to reach the next block.
		, List<IMutationEntity<IMutableCreatureEntity>> stepsToNextMove
		// This data field is defined by helpers based on the type (remember that it is NOT persistent).
		, Object extendedData
)
{
	/**
	 * A helper to handle the common case of needing to create one of these with default/starting values.
	 * 
	 * @param id The creature ID is expected to be negative.
	 * @param type The type of creature.
	 * @param location The starting location.
	 * @param health The starting health.
	 * @return A new creature with reasonable defaults for other fields.
	 */
	public static CreatureEntity create(int id
		, EntityType type
		, EntityLocation location
		, byte health
	)
	{
		Assert.assertTrue(id < 0);
		Assert.assertTrue(null != type);
		Assert.assertTrue(health > 0);
		
		return new CreatureEntity(id
				, type
				, location
				, 0.0f
				, health
				, 0L
				, null
				, null
		);
	}
}
