package com.jeffdisher.october.types;

import java.util.List;

import com.jeffdisher.october.aspects.MiscConstants;
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
		// This data is defined by EntityType, per-instance, and is persisted to disk and sent over the network.
		, Object extendedData
		
		// Note that ephemeral data isn't persisted or passed over the network.
		, Ephemeral ephemeral
)
{
	public static final int NO_TARGET_ENTITY_ID = 0;
	/**
	 * The amount of time a hostile mob will continue to live if not taking any deliberate action before despawn (5
	 * minutes).
	 */
	public static final long MILLIS_UNTIL_NO_ACTION_DESPAWN = 5L * 60L * 1_000L;
	/**
	 * We try to distribute the time to first move, after load, into this many seconds (we use the ID to distribute).
	 */
	public static final int LIMIT_SECONDS_TO_FIRST_MOVE = 10;

	public static final Ephemeral createEmptyEphemeral(int id, long gameTimeMillis)
	{
		int secondsToAdd = Math.abs(id) % LIMIT_SECONDS_TO_FIRST_MOVE;
		return new Ephemeral(null
			, gameTimeMillis + (1000L * (long)secondsToAdd)
			, gameTimeMillis + MILLIS_UNTIL_NO_ACTION_DESPAWN
			, gameTimeMillis
			, gameTimeMillis + MiscConstants.DAMAGE_TAKEN_TIMEOUT_MILLIS
		);
	}

	/**
	 * All data stored in this class is considered ephemeral and local:  It is not persisted, nor sent over the network.
	 */
	public static record Ephemeral(
		MovementPlan movementPlan
		// The next millisecond time when the creature should choose a target (location or entity).
		, long nextMovementPlanMillis
		// The millisecond time when the creature should despawn (if it is a despawning type).
		, long despawnMillis
		// The next millisecond time when the creature can issue a special action.
		, long nextActionMillis
		// The next millisecond time when the creature can take damage.
		, long nextTakeDamageMillis
	) {}

	/**
	 * Contains information related to the movement plan the creature is following.  This includes representations of
	 * the planned path but also information related to the target (if there is one).
	 */
	public static record MovementPlan(
		// The full plan, starting with the next step the creature must enter.  Can be null but never empty.
		List<AbsoluteLocation> fullPlan
		// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
		, int targetEntityId
		// The last location of the target which was used to determine the movementPlan (can be null if we have no
		// target or aren't tracking the current one).
		, EntityLocation targetPreviousLocation
		// In the cases where we need a very short walk, or where we are going to take a short-cut through fullPlan, we
		// use this location.
		, EntityLocation directLocation
	) {}

	/**
	 * A helper to handle the common case of needing to create one of these with default/starting values.
	 * 
	 * @param id The creature ID is expected to be negative.
	 * @param type The type of creature.
	 * @param location The starting location.
	 * @param gameTimeMillis The most recent game time, in case the instance needs to track relative timeouts, etc.
	 * @return A new creature with reasonable defaults for other fields.
	 */
	public static CreatureEntity create(int id
		, EntityType type
		, EntityLocation location
		, long gameTimeMillis
	)
	{
		Assert.assertTrue(id < 0);
		Assert.assertTrue(null != type);
		
		return new CreatureEntity(id
				, type
				, location
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, type.maxHealth()
				, MiscConstants.MAX_BREATH
				, type.extension().buildDefaultExtendedData(gameTimeMillis)
				
				, createEmptyEphemeral(id, gameTimeMillis)
		);
	}
}
