package com.jeffdisher.october.types;


/**
 * The minimal part of an Entity which is common for all kinds of moving entities within the world (be they player,
 * animal, monster, or something else).
 * This type purely exists for read-only actions within IMutationEntity objects.
 */
public record MinimalEntity(int id
		, EntityType type
		// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
		, EntityLocation location
)
{
	public static MinimalEntity fromEntity(Entity entity)
	{
		MinimalEntity result = null;
		if (null != entity)
		{
			result = new MinimalEntity(entity.id()
					, EntityType.PLAYER
					, entity.location()
			);
		}
		return result;
	}

	public static MinimalEntity fromCreature(CreatureEntity creature)
	{
		MinimalEntity result = null;
		if (null != creature)
		{
			result = new MinimalEntity(creature.id()
					, creature.type()
					, creature.location()
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
					, entity.type()
					, entity.location()
			);
		}
		return result;
	}
}
