package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;


/**
 * A variant of "Entity" for the cases where this is something which can move in the world, but the client can't see its
 * internal information.  For example, when walking another player's entity walk in the world, you only know some basics
 * like its location, volume, and movement vector, not its inventory or food level.
 */
public record PartialEntity(int id
		, EntityType type
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// Yaw is measured from [-128..127] where 0 is "North" and positive values move to the "left" (counter-clockwise, from above).
		, byte yaw
		// Pitch is measured from [-64..64] where 0 is "level", -64 is "straight down", and 64 is "straight up".
		, byte pitch
		, byte health
		// The extended data is based on type.
		, Object extendedData
)
{
	public static PartialEntity fromEntity(Entity entity)
	{
		// In this case, we don't have the current time since player entities don't have meaningful extended data.
		long gameTimeMillis = 0L;
		Environment env = Environment.getShared();
		return new PartialEntity(entity.id()
				, env.creatures.PLAYER
				, entity.location()
				, entity.yaw()
				, entity.pitch()
				, entity.health()
				, env.creatures.PLAYER.extendedCodec().buildDefault(gameTimeMillis)
		);
	}

	public static PartialEntity fromCreature(CreatureEntity entity)
	{
		return new PartialEntity(entity.id()
				, entity.type()
				, entity.location()
				, entity.yaw()
				, entity.pitch()
				, entity.health()
				, entity.extendedData()
		);
	}
}
