package com.jeffdisher.october.logic;

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
	 * @param reader Used to check viscosity of blocks in the world.
	 * @param targetLocation The target location of the entity.
	 * @param volume The volume of the entity.
	 * @return True if the entity can fit in the block space rooted in targetLocation.
	 */
	public static boolean canExistInLocation(ViscosityReader reader, EntityLocation targetLocation, EntityVolume volume)
	{
		return _canExistInLocation(reader, targetLocation, volume);
	}

	/**
	 * Returns true if the given location is standing directly solid blocks somewhere within its volume or is stuck in a
	 * block (since stuck in a block can't move, they are "unable to move down").
	 * 
	 * @param reader Used to check viscosity of blocks in the world.
	 * @param location The location where the entity is.
	 * @param volume The volume of the entity.
	 * @return True if the entity is standing directly on at least one solid block.
	 */
	public static boolean isStandingOnGround(ViscosityReader reader, EntityLocation location, EntityVolume volume)
	{
		return _isStandingOnGround(reader, location, volume);
	}

	/**
	 * Returns true if the given location is pressed directly against a solid block ceiling somewhere within its volume
	 * or is stuck in a block (since stuck in a block can't move, they are "unable to move up").
	 * 
	 * @param reader Used to check viscosity of blocks in the world.
	 * @param location The location where the entity is.
	 * @param volume The volume of the entity.
	 * @return True if the entity's head is pressed directly against at least one solid block.
	 */
	public static boolean isTouchingCeiling(ViscosityReader reader, EntityLocation location, EntityVolume volume)
	{
		// We will just use the EntityMovementHelpers for this, showing what happens when we move up.
		_CollisionHelper helper = new _CollisionHelper(reader);
		EntityMovementHelpers.interactiveEntityMove(location, volume, new EntityLocation(0.0f, 0.0f, 1.0f), helper);
		// If we are still in the same place, we must be touching the ceiling.
		return location.equals(helper.finalLocation);
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
	 * Finds the distance from the eye of a player with base at eyeEntityBase to the bounding box of the given target.
	 * 
	 * @param eyeEntityBase The base of the player whose eye we are looking through.
	 * @param playerType The type to use when finding the eye of the player.
	 * @param targetBase The base of the target volume to check.
	 * @param targetVolume The target volume size.
	 * @return The diagonal distance from the eye to the target's bounding-box.
	 */
	public static float distanceFromPlayerEyeToVolume(EntityLocation eyeEntityBase, EntityType playerType, EntityLocation targetBase, EntityVolume targetVolume)
	{
		EntityLocation eye = _getEyeLocation(eyeEntityBase, playerType);
		
		return _distanceToTarget(eye, targetBase, targetVolume);
	}

	/**
	 * Finds the distance from the eye of the given eyeEntity to the nearest edge of the given block.
	 * 
	 * @param eyeEntity The entity whose eye we are looking through.
	 * @param block The block we are targeting.
	 * @return The diagonal distance from the eye to the nearest edge of the given block.
	 */
	public static float distanceFromMutableEyeToBlockSurface(IMutablePlayerEntity eyeEntity, AbsoluteLocation block)
	{
		EntityLocation eye = _getEyeLocation(eyeEntity.getLocation(), eyeEntity.getType());
		EntityLocation target = block.toEntityLocation();
		EntityVolume cubeVolume = new EntityVolume(1.0f, 1.0f);
		
		return _distanceToTarget(eye, target, cubeVolume);
	}

	/**
	 * Finds the distance from the eye of a player with base at eyeEntityBase to the nearest edge of the given block.
	 * 
	 * @param eyeEntityBase The base of the player whose eye we are looking through.
	 * @param playerType The type to use when finding the eye of the player.
	 * @param block The block we are targeting.
	 * @return The diagonal distance from the eye to the nearest edge of the given block.
	 */
	public static float distanceFromPlayerEyeToBlockSurface(EntityLocation eyeEntityBase, EntityType playerType, AbsoluteLocation block)
	{
		EntityLocation eye = _getEyeLocation(eyeEntityBase, playerType);
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

	/**
	 * Returns the positive fractional component of a floating-point number (for X.YY, returns 0.YY).  Note that this
	 * will always return a positive value so something like -6.7 will return 0.3.
	 * This is used to determine how far from the base of a block a location is (hence the odd negative number
	 * behaviour).
	 * 
	 * @param f The float to examine.
	 * @return The positive fractional component (difference above floor).
	 */
	public static float getPositiveFractionalComponent(float f)
	{
		return f - (float)Math.floor(f);
	}

	/**
	 * Gets the centre-point of a region based at base of volume.
	 * 
	 * @param base The bottom-south-west corner of the region.
	 * @param volume The total volume of the region.
	 * @return The centre of the region.
	 */
	public static EntityLocation getCentreOfRegion(EntityLocation base, EntityVolume volume)
	{
		float width = volume.width() / 2.0f;
		float height = volume.height() / 2.0f;
		return new EntityLocation(base.x() + width, base.y() + width, base.z() + height);
	}


	private static boolean _isBlockAligned(float coord)
	{
		// TODO:  This technique could have problems with numbers "close" to whole, so we probably want a minimum delta if rounding errors could appear here.
		return (coord == (float)Math.round(coord));
	}

	private static boolean _canExistInLocation(ViscosityReader reader, EntityLocation targetLocation, EntityVolume volume)
	{
		// We will just use the EntityMovementHelpers for this, seeing if we collide with anything while not moving.
		_CollisionHelper helper = new _CollisionHelper(reader);
		EntityMovementHelpers.interactiveEntityMove(targetLocation, volume, new EntityLocation(0.0f, 0.0f, 0.0f), helper);
		return !helper.isStuckInBlock;
	}

	private static boolean _isStandingOnGround(ViscosityReader reader, EntityLocation location, EntityVolume volume)
	{
		// We will just use the EntityMovementHelpers for this, showing what happens with a fall.
		_CollisionHelper helper = new _CollisionHelper(reader);
		EntityMovementHelpers.interactiveEntityMove(location, volume, new EntityLocation(0.0f, 0.0f, -1.0f), helper);
		// If we are still in the same place, we must be on the ground.
		return location.equals(helper.finalLocation);
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


	private static class _CollisionHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final ViscosityReader _reader;
		public EntityLocation finalLocation;
		public boolean isStuckInBlock;
		public _CollisionHelper(ViscosityReader reader)
		{
			_reader = reader;
		}
		@Override
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
		{
			return _reader.getViscosityFraction(location, fromAbove);
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			this.finalLocation = finalLocation;
			this.isStuckInBlock = cancelX && cancelY && cancelZ;
		}
	}
}
