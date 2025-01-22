package com.jeffdisher.october.types;

import java.util.List;

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
		// We track the current entity velocity using an EntityLocation object since it is 3 orthogonal floats.
		// Note that horizontal movement is usually cancelled by friction within the same tick.
		, EntityLocation velocity
		// Yaw is measured from [-128..127] where 0 is "North" and positive values move to the "left" (counter-clockwise, from above).
		, byte yaw
		// Pitch is measured from [-64..64] where 0 is "level", -64 is "straight down", and 64 is "straight up".
		, byte pitch
		// The health value of the entity.  Currently, we just use a byte since it is in the range of [1..100].
		, byte health
		// The breath the entity has (for drowning).
		, byte breath
		
		// ----- Data elements below this line are considered ephemeral and will NOT be persisted. -----
		// The current plan of steps to the creature should be following.
		, List<AbsoluteLocation> movementPlan
		// The next tick where we will attempt to make a deliberate act.
		, long nextDeliberateActTick
		// The next tick where we will attempt to make an idle movement, if there is nothing deliberate to do.
		, long nextIdleActTick
		// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
		, int targetEntityId
		// The last block location of the target which was used to determine the movementPlan.
		, AbsoluteLocation targetPreviousLocation
		// This data field is defined by helpers based on the type (remember that it is NOT persistent).
		, Object extendedData
)
{
	public static final int NO_TARGET_ENTITY_ID = 0;

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
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, health
				, EntityConstants.MAX_BREATH
				
				, null
				, 0L
				, 0L
				, NO_TARGET_ENTITY_ID
				, null
				, null
		);
	}
}
