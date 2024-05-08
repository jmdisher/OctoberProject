package com.jeffdisher.october.types;


/**
 * The minimal part of an Entity which is common for all kinds of moving entities within the world (be they player,
 * animal, monster, or something else).
 * This type purely exists for read-only actions within IMutationEntity objects.
 */
public record MinimalEntity(int id
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
		// We track the current z-velocity in blocks per second, up.
		, float zVelocityPerSecond
		, EntityVolume volume
)
{
	public static MinimalEntity fromEntity(Entity entity)
	{
		MinimalEntity result = null;
		if (null != entity)
		{
			result = new MinimalEntity(entity.id()
					, entity.location()
					, entity.zVelocityPerSecond()
					, entity.volume()
			);
		}
		return result;
	}

	public static MinimalEntity fromPartialEntity(PartialEntity entity)
	{
		MinimalEntity result = null;
		if (null != entity)
		{
			result = new MinimalEntity(entity.id()
					, entity.location()
					, entity.zVelocityPerSecond()
					, entity.volume()
			);
		}
		return result;
	}
}
