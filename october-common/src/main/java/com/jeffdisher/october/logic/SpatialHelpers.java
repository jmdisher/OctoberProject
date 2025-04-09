package com.jeffdisher.october.logic;

import java.util.function.Function;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MinimalEntity;


/**
 * A collection of static helper methods to help us make sense of the environment in higher-level ways.
 * To be clear regarding our interpretation of the XYZ coordinates:
 * -x is West-East, such that -x is West, +x is East
 * -y is South-North, such that -y is South, +y is North
 * -z is down-up, such that -z is down, +z is up
 * -the XYZ location where an entity "is" is the air block where their feet exist.
 * This means that the .0 location of any single block is considered to be the bottom, South-West corner.  The entity
 * volume then extends in the East-North-Up direction from that corner.
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
			Environment env = Environment.getShared();
			// Our head is right against the block layer above us so we will conclude we are touching the ceiling if any of them are non-air.
			// In this case, we want to check that ALL of the blocks in the next layer are air and then revert the returned value to see if ANY were non-air (solid ceiling).
			Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
				boolean isAir;
				BlockProxy block = blockLookup.apply(target);
				// This can be null if the world isn't totally loaded on the client.
				if (null != block)
				{
					isAir = !env.blocks.isSolid(block.getBlock());
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

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the ceiling and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousZ The previous z location where the entity was standing before it tried to rise.
	 * @return The safe location against the ceiling or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingCeiling(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousZ)
	{
		// We were jumping to see if we can clamp our location under the block.
		float headTop = start.z() + volume.height();
		// Round this down and see if we can fit until this block.
		float zTop = (float) Math.floor(headTop);
		float zToCheck = zTop - volume.height();
		EntityLocation match = null;
		while ((null == match) && (zToCheck >= previousZ))
		{
			EntityLocation checkLocation = new EntityLocation(start.x(), start.y(), zToCheck);
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block down (unlikely that there is more than one iteration here).
				zToCheck -= 1.0f;
			}
		}
		return match;
	}

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the ground and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousZ The previous z location where the entity was standing before it tried to fall.
	 * @return The safe location on the ground or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingGround(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousZ)
	{
		// We were falling so see if we can stop on the block(s) above where we fell.
		float zToCheck = (float) Math.ceil(start.z());
		// WARNING:  This approach for "finding the ground" can be exploited by nefarious clients to avoid
		// falling at all so we only check strictly less than oldZ.
		EntityLocation match = null;
		while ((null == match) && (zToCheck < previousZ))
		{
			EntityLocation checkLocation = new EntityLocation(start.x(), start.y(), zToCheck);
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block up.
				zToCheck += 1.0f;
			}
		}
		return match;
	}

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the East wall and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousX The previous x location where the entity was standing before it tried to move.
	 * @return The safe location against the East wall or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingEastWall(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousX)
	{
		float width = volume.width();
		float eastEdge = start.x() + width;
		// Round this down and see if we can fit until this block.
		float xTop = (float) Math.floor(eastEdge);
		float xToCheck = xTop - width;
		EntityLocation match = null;
		while ((null == match) && (xToCheck > previousX))
		{
			EntityLocation checkLocation = new EntityLocation(xToCheck, start.y(), start.z());
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block over (unlikely that there is more than one iteration here).
				xToCheck -= 1.0f;
			}
		}
		return match;
	}

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the West wall and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousX The previous x location where the entity was standing before it tried to move.
	 * @return The safe location against the West wall or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingWestWall(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousX)
	{
		float xToCheck = (float) Math.ceil(start.x());
		EntityLocation match = null;
		while ((null == match) && (xToCheck < previousX))
		{
			EntityLocation checkLocation = new EntityLocation(xToCheck, start.y(), start.z());
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block up.
				xToCheck += 1.0f;
			}
		}
		return match;
	}

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the North wall and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousY The previous y location where the entity was standing before it tried to move.
	 * @return The safe location against the North wall or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingNorthWall(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousY)
	{
		float width = volume.width();
		float northEdge = start.y() + width;
		// Round this down and see if we can fit until this block.
		float yTop = (float) Math.floor(northEdge);
		float yToCheck = yTop - width;
		EntityLocation match = null;
		while ((null == match) && (yToCheck > previousY))
		{
			EntityLocation checkLocation = new EntityLocation(start.x(), yToCheck, start.z());
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block over (unlikely that there is more than one iteration here).
				yToCheck -= 1.0f;
			}
		}
		return match;
	}

	/**
	 * Finds the location closest to start where an entity with the given volume will be touching the South wall and can
	 * exist without colliding with other blocks.
	 * 
	 * @param blockLookup Looks up blocks in the world.
	 * @param start The location where the entity placement was attempted.
	 * @param volume The volume of the entity.
	 * @param previousY The previous y location where the entity was standing before it tried to move.
	 * @return The safe location against the South wall or null if one couldn't be found.
	 */
	public static EntityLocation locationTouchingSouthWall(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation start, EntityVolume volume, float previousY)
	{
		float yToCheck = (float) Math.ceil(start.y());
		EntityLocation match = null;
		while ((null == match) && (yToCheck < previousY))
		{
			EntityLocation checkLocation = new EntityLocation(start.x(), yToCheck, start.z());
			if (_canExistInLocation(blockLookup, checkLocation, volume))
			{
				match = checkLocation;
			}
			else
			{
				// Try the next block up.
				yToCheck += 1.0f;
			}
		}
		return match;
	}

	/**
	 * Returns true if the given coordinate is block-aligned.  This means that it will be close enough to a X.00f value
	 * for us to assume that it _is_ that integer value.
	 * 
	 * @param coord The coordinate to check.
	 * @return True if this is aligned.
	 */
	public static boolean isBlockAligned(float coord)
	{
		return _isBlockAligned(coord);
	}

	/**
	 * The location of the entity's feet (the centre of their model, at the very bottom).
	 * 
	 * @param entity The entity.
	 * @return The location where their feet are.
	 */
	public static EntityLocation getCentreFeetLocation(IMutableMinimalEntity entity)
	{
		return _getCentreFeetLocation(entity);
	}

	/**
	 * The block location where the entity's feet are located.
	 * 
	 * @param entity The entity.
	 * @return The block where the bottom of the entity's feet are.
	 */
	public static AbsoluteLocation getBlockAtFeet(IMutableMinimalEntity entity)
	{
		return _getCentreFeetLocation(entity).getBlockLocation();
	}

	/**
	 * Finds the location of the entity's eyes.  This is the centre of their model, near the top.
	 * 
	 * @param entity The entity.
	 * @return The location where their eyes are.
	 */
	public static EntityLocation getEyeLocation(IMutablePlayerEntity entity)
	{
		return _getEyeLocation(entity.getLocation(), entity.getType());
	}

	/**
	 * Finds the distance from the eye of the given eyeEntity to the bounding box of the given targetEntity.
	 * 
	 * @param eyeEntity The entity whose eye we are looking through.
	 * @param targetEntity The entity we are targeting.
	 * @return The diagonal distance from the eye to the target's bounding-box.
	 */
	public static float distanceFromMutableEyeToEntitySurface(IMutableMinimalEntity eyeEntity, MinimalEntity targetEntity)
	{
		EntityLocation eye = _getEyeLocation(eyeEntity.getLocation(), eyeEntity.getType());
		EntityLocation target = targetEntity.location();
		EntityVolume targetVolume = targetEntity.type().volume();
		
		return _distanceToTarget(eye, target, targetVolume);
	}

	/**
	 * Finds the distance from the eye of a player with base at eyeEntityBase to the bounding box of the given
	 * targetEntity.
	 * 
	 * @param eyeEntityBase The base of the player whose eye we are looking through.
	 * @param playerType The type to use when finding the eye of the player.
	 * @param targetEntity The entity we are targeting.
	 * @return The diagonal distance from the eye to the target's bounding-box.
	 */
	public static float distanceFromPlayerEyeToEntitySurface(EntityLocation eyeEntityBase, EntityType playerType, MinimalEntity targetEntity)
	{
		EntityLocation eye = _getEyeLocation(eyeEntityBase, playerType);
		EntityLocation target = targetEntity.location();
		EntityVolume targetVolume = targetEntity.type().volume();
		
		return _distanceToTarget(eye, target, targetVolume);
	}

	/**
	 * Finds the distance from the eye of the given eyeEntity to the nearest edge of the given block.
	 * 
	 * @param eyeEntity The entity whose eye we are looking through.
	 * @param block The block we are targeting.
	 * @return The diagonal distance from the eye to the nearest edge of the given block.
	 */
	public static float distanceFromEyeToBlockSurface(IMutablePlayerEntity eyeEntity, AbsoluteLocation block)
	{
		EntityLocation eye = _getEyeLocation(eyeEntity.getLocation(), eyeEntity.getType());
		EntityLocation target = block.toEntityLocation();
		EntityVolume cubeVolume = new EntityVolume(1.0f, 1.0f);
		
		return _distanceToTarget(eye, target, cubeVolume);
	}

	/**
	 * Finds the distance from a start location to the nearest of a target base and volume .
	 * 
	 * @param start The start location from which to measure.
	 * @param targetBase The base of the target region.
	 * @param targetVolume The volume of the target region.
	 * @return The diagonal distance from the start to the nearest edge of the described region.
	 */
	public static float distanceFromLocationToVolume(EntityLocation start, EntityLocation targetBase, EntityVolume targetVolume)
	{
		return _distanceToTarget(start, targetBase, targetVolume);
	}


	private static boolean _isBlockAligned(float coord)
	{
		// TODO:  This technique could have problems with numbers "close" to whole, so we probably want a minimum delta if rounding errors could appear here.
		return (coord == (float)Math.round(coord));
	}

	private static boolean _canExistInLocation(Function<AbsoluteLocation, BlockProxy> blockLookup, EntityLocation targetLocation, EntityVolume volume)
	{
		Environment env = Environment.getShared();
		// We will just check that the blocks in the target location occupied by the volume are all air (this will need to be generalized to non-colliding, later).
		Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
			boolean canExist;
			BlockProxy block = blockLookup.apply(target);
			// This can be null if the world isn't totally loaded on the client.
			if (null != block)
			{
				canExist = !env.blocks.isSolid(block.getBlock());
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
			Environment env = Environment.getShared();
			// This is on a block so check it.
			// In this case, we want to check that ALL of the blocks in the ground are air and then revert the returned value to see if ANY were non-air (ground).
			Predicate<AbsoluteLocation> checkPredicate = (AbsoluteLocation target) -> {
				boolean isAir;
				BlockProxy block = blockLookup.apply(target);
				// This can be null if the world isn't totally loaded on the client.
				if (null != block)
				{
					isAir = !env.blocks.isSolid(block.getBlock());
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

	private static EntityLocation _getCentreFeetLocation(IMutableMinimalEntity entity)
	{
		EntityLocation entityLocation = entity.getLocation();
		EntityVolume volume = entity.getType().volume();
		// (we want the block under our centre).
		float widthOffset = volume.width() / 2.0f;
		return new EntityLocation(entityLocation.x() + widthOffset, entityLocation.y() + widthOffset, entityLocation.z());
	}

	private static EntityLocation _getEyeLocation(EntityLocation entityLocation, EntityType type)
	{
		// The location is the bottom-south-west corner so we want to offset by half their width and most of their height.
		// We will say that their eyes are 90% of the way up their body from their feet.
		float entityEyeHeightMultiplier = 0.9f;
		EntityVolume volume = type.volume();
		
		float widthOffset = volume.width() / 2.0f;
		float heightOffset = volume.height() * entityEyeHeightMultiplier;
		return new EntityLocation(entityLocation.x() + widthOffset, entityLocation.y() + widthOffset, entityLocation.z() + heightOffset);
	}

	private static float _distanceToTarget(EntityLocation eye, EntityLocation target, EntityVolume targetVolume)
	{
		// We interpret this as a rectangular prism axis-aligned bounding-box.
		float highX = target.x() + targetVolume.width();
		float highY = target.y() + targetVolume.width();
		float highZ = target.z() + targetVolume.height();
		float squareDistance = 0.0f;
		
		if (eye.x() < target.x())
		{
			float delta = target.x() - eye.x();
			squareDistance += delta * delta;
		}
		else if (eye.x() > highX)
		{
			float delta = eye.x() - highX;
			squareDistance += delta * delta;
		}
		if (eye.y() < target.y())
		{
			float delta = target.y() - eye.y();
			squareDistance += delta * delta;
		}
		else if (eye.y() > highY)
		{
			float delta = eye.y() - highY;
			squareDistance += delta * delta;
		}
		if (eye.z() < target.z())
		{
			float delta = target.z() - eye.z();
			squareDistance += delta * delta;
		}
		else if (eye.z() > highZ)
		{
			float delta = eye.z() - highZ;
			squareDistance += delta * delta;
		}
		return (float)Math.sqrt(squareDistance);
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
