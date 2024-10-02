package com.jeffdisher.october.types;

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

	public static EntityVolume getVolume(EntityType type)
	{
		EntityVolume volume;
		switch (type)
		{
		case PLAYER:
			volume = VOLUME_PLAYER;
			break;
		case COW:
			volume = VOLUME_COW;
			break;
		case ORC:
			volume = VOLUME_ORC;
			break;
		default:
			throw Assert.unreachable();
		}
		return volume;
	}

	public static float getBlocksPerSecondSpeed(EntityType type)
	{
		float speed;
		switch (type)
		{
		case PLAYER:
			speed = SPEED_PLAYER;
			break;
		case COW:
			speed = SPEED_COW;
			break;
		case ORC:
			speed = SPEED_ORC;
			break;
		default:
			throw Assert.unreachable();
		}
		return speed;
	}
}
