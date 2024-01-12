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

	/**
	 * Returns true if the given location is standing directly on a non-air tile in at least one of the blocks within its volume.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param location The location where the entity is.
	 * @param volume The volume of the entity.
	 * @return True if the entity is standing directly on at least one non-air tile.
	 */
	public static boolean isStandingOnGround(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation location, EntityVolume volume)
	{
		return _isStandingOnGround(blockLookup, location, volume);
	}

	/**
	 * Returns true if the given location is pressed directly up against and overhead non-air block in at least one of the blocks with its volume
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param location The location where the entity is.
	 * @param volume The volume of the entity.
	 * @return True if the entity's head is pressed directly against at least one non-air tile.
	 */
	public static boolean isTouchingCeiling(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation location, EntityVolume volume)
	{
		// First, we need to figure out where our head is.
		boolean isTouchingCeiling;
		float headTop = location.z() + volume.height();
		if (_isBlockAligned(headTop))
		{
			// Our head is right against the block layer above us so we will conclude we are touching the ceiling if any of them are non-air.
			// In this case, we want to check that ALL of the blocks in the next layer are air and then revert the returned value to see if ANY were non-air (solid ceiling).
			Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
				boolean isAir;
				BlockProxy block = blockLookup.apply(target);
				// This can be null if the world isn't totally loaded on the client.
				if (null != block)
				{
					isAir = (ItemRegistry.AIR.number() == block.getData15(AspectRegistry.BLOCK));
				}
				else
				{
					isAir = false;
				}
				return isAir;
			};
			// We just want to check the single block so provide a tiny height (cannot be zero due to ceiling rounding).
			EntityLocation headLocation = new EntityLocation(location.x(), location.y(), headTop);
			boolean allAir = _blocksInVolumeCheck(checkPredicate, headLocation, volume.width(), 0.1f);
			isTouchingCeiling = !allAir;
		}
		else
		{
			// We are mid-block.
			isTouchingCeiling = false;
		}
		return isTouchingCeiling;
	}


	private static boolean _isBlockAligned(float coord)
	{
		// TODO:  This technique could have problems with numbers "close" to whole, so we probably want a minimum delta if rounding errors could appear here.
		return (coord == (float)Math.round(coord));
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

	private static boolean _isStandingOnGround(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation location, EntityVolume volume)
	{
		// First, if we are floating mid-block, we assume that we are not on the ground (will need to change for stairs, later).
		boolean isOnGround;
		if (_isBlockAligned(location.z()))
		{
			// This is on a block so check it.
			// In this case, we want to check that ALL of the blocks in the ground are air and then revert the returned value to see if ANY were non-air (ground).
			Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
				boolean isAir;
				BlockProxy block = blockLookup.apply(target);
				// This can be null if the world isn't totally loaded on the client.
				if (null != block)
				{
					isAir = (ItemRegistry.AIR.number() == block.getData15(AspectRegistry.BLOCK));
				}
				else
				{
					isAir = false;
				}
				return isAir;
			};
			// We just want to check the single block so provide a tiny height (cannot be zero due to ceiling rounding).
			EntityLocation groundLocation = new EntityLocation(location.x(), location.y(), location.z() - 1.0f);
			boolean allAir = _blocksInVolumeCheck(checkPredicate, groundLocation, volume.width(), 0.1f);
			isOnGround = !allAir;
		}
		else
		{
			// This is floating in a block, so not on the ground.
			isOnGround = false;
		}
		return isOnGround;
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
