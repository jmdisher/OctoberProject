package com.jeffdisher.october.creatures;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains information around the physical volumes of creatures within the environment.
 * Note that this will likely be mostly moved into a data file, in the future.
 */
public class CreatureVolumes
{
	// Volume constants for different creature types.
	public static final EntityVolume VOLUME_COW = new EntityVolume(0.7f, 0.8f);
	public static final EntityVolume VOLUME_ORC = new EntityVolume(0.7f, 0.4f);

	public static EntityVolume getVolume(CreatureEntity creature)
	{
		EntityVolume volume;
		switch (creature.type())
		{
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
}
