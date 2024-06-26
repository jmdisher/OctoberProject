package com.jeffdisher.october.types;


/**
 * A variant of "Entity" for the cases where this is something which can move in the world, but the client can't see its
 * internal information.  For example, when walking another player's entity walk in the world, you only know some basics
 * like its location, volume, and movement vector, not its inventory or food level.
 */
public record PartialEntity(int id
		, EntityType type
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// We track the current z-velocity in blocks per second, up.
		, float zVelocityPerSecond
		, EntityVolume volume
)
{
	public static PartialEntity fromEntity(Entity entity)
	{
		return new PartialEntity(entity.id()
				, EntityType.PLAYER
				, entity.location()
				, entity.zVelocityPerSecond()
				, entity.volume()
		);
	}

	public static PartialEntity fromCreature(CreatureEntity entity)
	{
		return new PartialEntity(entity.id()
				, entity.type()
				, entity.location()
				, entity.zVelocityPerSecond()
				, EntityConstants.getVolume(entity.type())
		);
	}
}
