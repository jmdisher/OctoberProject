package com.jeffdisher.october.logic;

import java.util.function.Function;
import java.util.function.Predicate;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;


/**
 * A collection of static helper methods to help us make sense of the environment in higher-level ways.
 */
public class SpatialHelpers
{
	/**
	 * A common helper exposed to other changes since some changes need a "move" aspect, and this allows common logic.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param targetLocation The target location of the entity.
	 * @param volume The volume of the entity.
	 * @return True if the entity can fit in the block space rooted in targetLocation.
	 */
	public static boolean canExistInLocation(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation targetLocation, EntityVolume volume)
	{
		return _canExistInLocation(blockLookup, targetLocation, volume);
	}


	private static boolean _canExistInLocation(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation targetLocation, EntityVolume volume)
	{
		// We will just check that the blocks in the target location occupied by the volume are all air (this will need to be generalized to non-colliding, later).
		Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
			boolean canExist;
			BlockProxy block = blockLookup.apply(target);
			// This can be null if the world isn't totally loaded on the client.
			if (null != block)
			{
				canExist = (ItemRegistry.AIR.number() == block.getData15(AspectRegistry.BLOCK));
			}
			else
			{
				canExist = false;
			}
			return canExist;
		};
		return _blocksInVolumeCheck(checkPredicate, targetLocation, volume.width(), volume.height());
	}

	private static boolean _blocksInVolumeCheck(Predicate<AbsoluteLocation> checkPredicate, EntityLocation targetLocation, float width, float height)
	{
		float x = targetLocation.x();
		int minX = _floorInt(x);
		int maxX = _ceilingInt(x + width - 1.0f);
		float y = targetLocation.y();
		int minY = _floorInt(y);
		int maxY = _ceilingInt(y + width - 1.0f);
		float z = targetLocation.z();
		int minZ = _floorInt(z);
		int maxZ = _ceilingInt(z + height - 1.0f);
		
		boolean check = true;
		for (int i = minX; check && (i <= maxX); ++i)
		{
			for (int j = minY; check && (j <= maxY); ++j)
			{
				for (int k = minZ; check && (k <= maxZ); ++k)
				{
					check = checkPredicate.test(new AbsoluteLocation(i, j, k));
				}
			}
		}
		return check;
	}

	private static int _floorInt(float f)
	{
		return (int) Math.floor(f);
	}

	private static int _ceilingInt(float f)
	{
		return (int) Math.ceil(f);
	}
}
