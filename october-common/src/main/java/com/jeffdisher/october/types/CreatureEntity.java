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
	 * The empty ephemeral data used when loading a new instance.
	 */
	public static final Ephemeral EMPTY_DATA = new Ephemeral(null
			, 0L
			, false
			, 0L
			, NO_TARGET_ENTITY_ID
			, null
			, 0L
			, false
			, null
			, 0L
	);

	/**
	 * All data stored in this class is considered ephemeral and local:  It is not persisted, nor sent over the network.
	 */
	public static record Ephemeral(
			// The current plan of steps to the creature should be following.
			List<AbsoluteLocation> movementPlan
			// The last game millisecond when this creature's AI made a decision or did something.
			, long lastActionMillis
			// If something special happens, we want to force a new deliberate action, no matter lastActionTick.
			, boolean shouldTakeImmediateAction
			// The last game millisecond where some action was taken to stop this creature from despawning (if it is a despawning type).
			, long despawnKeepAliveMillis
			// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
			, int targetEntityId
			// The last block location of the target which was used to determine the movementPlan.
			, AbsoluteLocation targetPreviousLocation
			// The last game millisecond when this creature last sent an attack.
			, long lastAttackMillis
			// True if this is a breedable creature which should now search for a partner.
			, boolean inLoveMode
			// Non-null if this is a breedable creature who is ready to spawn offspring.
			, EntityLocation offspringLocation
			// The millisecond time when this creature last took damage.
			, long lastDamageTakenMillis
	) {}

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
				, MiscConstants.MAX_BREATH
				, type.extendedCodec().buildDefault()
				
				, EMPTY_DATA
		);
	}

	public CreatureEntity updateKeepAlive(long gameMillis)
	{
		return new CreatureEntity(this.id
				, this.type
				, this.location
				, this.velocity
				, this.yaw
				, this.pitch
				, this.health
				, this.breath
				, this.type.extendedCodec().buildDefault()
				
				, new Ephemeral(
						this.ephemeral.movementPlan
						, this.ephemeral.lastActionMillis
						, this.ephemeral.shouldTakeImmediateAction
						, gameMillis
						, this.ephemeral.targetEntityId
						, this.ephemeral.targetPreviousLocation
						, this.ephemeral.lastAttackMillis
						, this.ephemeral.inLoveMode
						, this.ephemeral.offspringLocation
						, this.ephemeral().lastDamageTakenMillis
				)
		);
	}
}
