package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.Environment;


/**
 * The minimal part of an Entity which is common for all kinds of moving entities within the world (be they player,
 * animal, monster, or something else).
 * This type purely exists for read-only actions within IMutationEntity objects.
 */
public record MinimalEntity(int id
	, EntityType type
	// Note that the location is the bottom, south-west corner of the space occupied by the entity and the volume extends from there.
	, EntityLocation location
	// The extended data is based on type.
	, Object extendedData
)
{
	public static MinimalEntity fromEntity(Entity entity)
	{
		MinimalEntity result = null;
		if (null != entity)
		{
			Environment env = Environment.getShared();
			result = new MinimalEntity(entity.id()
				, env.creatures.PLAYER
				, entity.location()
				, env.creatures.PLAYER.extension().buildDefaultExtendedData(0L)
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
				, creature.extendedData()
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
				, entity.extendedData()
			);
		}
		return result;
	}
}
