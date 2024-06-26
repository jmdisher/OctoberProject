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
	public static final float SPEED_COW = 4.0f;
	public static final float SPEED_ORC = 4.0f;

	public static EntityVolume getVolume(CreatureEntity creature)
	{
		EntityVolume volume;
		switch (creature.type())
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

	public static float getBlocksPerSecondSpeed(CreatureEntity creature)
	{
		float speed;
		switch (creature.type())
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
