package com.jeffdisher.october.pathing;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.utils.Assert;


/**
 * Path-finding is done on a finer granularity than the usual AbsoluteLocation (for reasons of dealing with sub-blocks)
 * so we use this type for those cases.
 */
public class PathLocation
{
	/**
	 * The number of path steps along the side of a block.
	 */
	public static final int PATH_PER_BLOCK = 2;
	/**
	 * The edge size of a single instance (1.0f would mean 1-to-1 with other units).
	 */
	public static final float PATH_UNIT = 1.0f / (float)PATH_PER_BLOCK;

	/**
	 * Creates an instance with the direct parameters it will internally use.  This is only intended for use in tests
	 * since it assumes internal parameters.
	 * 
	 * @param location The absolute location to use.
	 * @param xOffset The X offset within the block.
	 * @param yOffset The Y offset within the block.
	 * @param zOffset The Z offset within the block.
	 * @return A new instance.
	 */
	public static PathLocation test_raw(AbsoluteLocation location, int xOffset, int yOffset, int zOffset)
	{
		Assert.assertTrue(xOffset < PATH_PER_BLOCK);
		Assert.assertTrue(yOffset < PATH_PER_BLOCK);
		Assert.assertTrue(zOffset < PATH_PER_BLOCK);
		return new PathLocation(location, xOffset, yOffset, zOffset);
	}

	/**
	 * Creates an instance where the entityBase is.
	 * 
	 * @param entityBase The base location of an entity.
	 * @return A new instance.
	 */
	public static PathLocation forEntityBase(EntityLocation entityBase)
	{
		AbsoluteLocation location = entityBase.getBlockLocation();
		int xOffset = _getFractionalOffset(entityBase.x());
		int yOffset = _getFractionalOffset(entityBase.y());
		int zOffset = _getFractionalOffset(entityBase.z());
		return new PathLocation(location, xOffset, yOffset, zOffset);
	}

	/**
	 * A utility to find the 3D Manhattan distance between 2 instances, in terms of steps (not necessarily similar to
	 * the units used in AbsoluteLocation or EntityLocation).
	 * 
	 * @param from The starting instance.
	 * @param to The ending instance.
	 * @return The number of steps from the given "from" to the given "to" location in terms of X+Y+Z moves.
	 */
	public static int pathStepDistance(PathLocation from, PathLocation to)
	{
		int fromXRaw = _getRawLocation(from._location.x(), from._xOffset);
		int fromYRaw = _getRawLocation(from._location.y(), from._yOffset);
		int fromZRaw = _getRawLocation(from._location.z(), from._zOffset);
		int toXRaw = _getRawLocation(to._location.x(), to._xOffset);
		int toYRaw = _getRawLocation(to._location.y(), to._yOffset);
		int toZRaw = _getRawLocation(to._location.z(), to._zOffset);
		
		return Math.abs(fromXRaw - toXRaw)
			+ Math.abs(fromYRaw - toYRaw)
			+ Math.abs(fromZRaw - toZRaw)
		;
	}

	/**
	 * A helper to determine the direction of travel from from to to.  Note that this internally asserts that these
	 * blocks are directly adjacent (share a face).
	 * 
	 * @param from The starting block.
	 * @param to The ending block.
	 * @return The direction of travel (null if blocks are the same).
	 */
	public static FacingDirection adjacentDirection(PathLocation from, PathLocation to)
	{
		FacingDirection direction = null;
		
		int fromXRaw = _getRawLocation(from._location.x(), from._xOffset);
		int fromYRaw = _getRawLocation(from._location.y(), from._yOffset);
		int fromZRaw = _getRawLocation(from._location.z(), from._zOffset);
		int toXRaw = _getRawLocation(to._location.x(), to._xOffset);
		int toYRaw = _getRawLocation(to._location.y(), to._yOffset);
		int toZRaw = _getRawLocation(to._location.z(), to._zOffset);
		
		if (fromXRaw > toXRaw)
		{
			direction = FacingDirection.WEST;
		}
		else if (fromXRaw < toXRaw)
		{
			direction = FacingDirection.EAST;
		}
		if (fromYRaw > toYRaw)
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.SOUTH;
		}
		else if (fromYRaw < toYRaw)
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.NORTH;
		}
		if (fromZRaw > toZRaw)
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.DOWN;
		}
		else if (fromZRaw < toZRaw)
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.UP;
		}
		return direction;
	}


	private final AbsoluteLocation _location;
	private final int _xOffset;
	private final int _yOffset;
	private final int _zOffset;

	private PathLocation(AbsoluteLocation location, int xOffset, int yOffset, int zOffset)
	{
		Assert.assertTrue(xOffset >= 0);
		Assert.assertTrue(yOffset >= 0);
		Assert.assertTrue(zOffset >= 0);
		Assert.assertTrue(xOffset < PATH_PER_BLOCK);
		Assert.assertTrue(yOffset < PATH_PER_BLOCK);
		Assert.assertTrue(zOffset < PATH_PER_BLOCK);
		
		_location = location;
		_xOffset = xOffset;
		_yOffset = yOffset;
		_zOffset = zOffset;
	}

	/**
	 * Creates a new instance based on the the given number of steps away from the receiver.
	 * 
	 * @param x The number of steps in the X axis.
	 * @param y The number of steps in the Y axis.
	 * @param z The number of steps in the Z axis.
	 * @return The new instance.
	 */
	public PathLocation getRelative(int x, int y, int z)
	{
		int combinedX = _xOffset + x;
		int combinedY = _yOffset + y;
		int combinedZ = _zOffset + z;
		
		int relX = combinedX / PATH_PER_BLOCK;
		int relY = combinedY / PATH_PER_BLOCK;
		int relZ = combinedZ / PATH_PER_BLOCK;
		
		int xOffset = combinedX % PATH_PER_BLOCK;
		if (xOffset < 0)
		{
			relX -= 1;
			xOffset += PATH_PER_BLOCK;
		}
		int yOffset = combinedY % PATH_PER_BLOCK;
		if (yOffset < 0)
		{
			relY -= 1;
			yOffset += PATH_PER_BLOCK;
		}
		int zOffset = combinedZ % PATH_PER_BLOCK;
		if (zOffset < 0)
		{
			relZ -= 1;
			zOffset += PATH_PER_BLOCK;
		}
		
		AbsoluteLocation newLocation = _location.getRelative(relX, relY, relZ);
		return new PathLocation(newLocation, xOffset, yOffset, zOffset);
	}

	/**
	 * @return The west-south-bottom corner of the receiver.
	 */
	public EntityLocation getBaseLocation()
	{
		float xOffset = PATH_UNIT * (float)_xOffset;
		float yOffset = PATH_UNIT * (float)_yOffset;
		float zOffset = PATH_UNIT * (float)_zOffset;
		
		return _location.toEntityLocation().getRelative(xOffset, yOffset, zOffset);
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean isEqual = false;
		if ((null != obj) && (obj.getClass() == this.getClass()))
		{
			PathLocation other = (PathLocation)obj;
			isEqual = _location.equals(other._location)
				&& (_xOffset == other._xOffset)
				&& (_yOffset == other._yOffset)
				&& (_zOffset == other._zOffset)
			;
		}
		return isEqual;
	}

	@Override
	public int hashCode()
	{
		return _location.hashCode()
			^ (_xOffset << 16)
			^ (_yOffset << 8)
			^ (_zOffset << 0)
		;
	}

	@Override
	public String toString()
	{
		return String.format("PathLocation(%s + X=%d Y=%d Z=%d)", _location, _xOffset, _yOffset, _zOffset);
	}


	private static int _getFractionalOffset(float f)
	{
		float fraction = f - (float)Math.floor(f);
		return (int)Math.floor(fraction / PATH_UNIT);
	}

	private static int _getRawLocation(int loc, int offset)
	{
		return PATH_PER_BLOCK * loc + offset;
	}
}
