package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.CreatureRegistry;
import com.jeffdisher.october.utils.Assert;


/**
 * Constants related to different kinds of entities in the world.
 * Note that this will likely be mostly moved into a data file, in the future.
 */
public class EntityConstants
{
	// Volume constants for different creature types.
	public static final EntityVolume VOLUME_PLAYER = new EntityVolume(0.9f, 0.4f);
	public static final EntityVolume VOLUME_COW = new EntityVolume(0.7f, 0.8f);
	public static final EntityVolume VOLUME_ORC = new EntityVolume(0.7f, 0.4f);

	// Horizontal speed constants in blocks per second.
	public static final float SPEED_PLAYER = 4.0f;
	public static final float SPEED_COW = 2.0f;
	public static final float SPEED_ORC = 3.0f;

	// Breath is just used as a percentage which will drop by 1% per second when not in a breathable block.
	public static final byte MAX_BREATH = 100;
	public static final byte SUFFOCATION_BREATH_PER_SECOND = 5;
	public static final byte SUFFOCATION_DAMAGE_PER_SECOND = 10;

	public static final byte COW_MAX_HEALTH = 40;
	public static final byte ORC_MAX_HEALTH = 20;
	public static final byte PLAYER_MAX_HEALTH = 100;

	public static final byte PLAYER_MAX_FOOD = 100;
	public static final byte STARVATION_DAMAGE_PER_SECOND = 5;

	public static EntityVolume getVolume(EntityType type)
	{
		EntityVolume volume;
		if (CreatureRegistry.PLAYER == type)
		{
			volume = VOLUME_PLAYER;
		}
		else if (CreatureRegistry.COW == type)
		{
			volume = VOLUME_COW;
		}
		else if (CreatureRegistry.ORC == type)
		{
			volume = VOLUME_ORC;
		}
		else
		{
			throw Assert.unreachable();
		}
		return volume;
	}

	public static float getBlocksPerSecondSpeed(EntityType type)
	{
		float speed;
		if (CreatureRegistry.PLAYER == type)
		{
			speed = SPEED_PLAYER;
		}
		else if (CreatureRegistry.COW == type)
		{
			speed = SPEED_COW;
		}
		else if (CreatureRegistry.ORC == type)
		{
			speed = SPEED_ORC;
		}
		else
		{
			throw Assert.unreachable();
		}
		return speed;
	}
}
