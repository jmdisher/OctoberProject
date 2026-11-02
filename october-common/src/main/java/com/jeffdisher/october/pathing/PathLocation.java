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
	public static final int PATH_PER_BLOCK = 1;
	/**
	 * The edge size of a single instance (1.0f would mean 1-to-1 with other units).
	 */
	public static final float PATH_UNIT = 1.0f / (float)PATH_PER_BLOCK;

	/**
	 * Creates an instance with the direct parameters it will internally use.  This is only intended for use in tests
	 * since it assumes internal parameters.
	 * 
	 * @param location The absolute location to use.
	 * @return A new instance.
	 */
	public static PathLocation test_raw(AbsoluteLocation location)
	{
		return new PathLocation(location);
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
		return new PathLocation(location);
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
		int fromXRaw = from._location.x();
		int fromYRaw = from._location.y();
		int fromZRaw = from._location.z();
		int toXRaw = to._location.x();
		int toYRaw = to._location.y();
		int toZRaw = to._location.z();
		
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
		
		int fromXRaw = from._location.x();
		int fromYRaw = from._location.y();
		int fromZRaw = from._location.z();
		int toXRaw = to._location.x();
		int toYRaw = to._location.y();
		int toZRaw = to._location.z();
		
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

	private PathLocation(AbsoluteLocation location)
	{
		_location = location;
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
		AbsoluteLocation newLocation = _location.getRelative(x, y, z);
		return new PathLocation(newLocation);
	}

	/**
	 * @return The west-south-bottom corner of the receiver.
	 */
	public EntityLocation getBaseLocation()
	{
		return _location.toEntityLocation();
	}

	@Override
	public boolean equals(Object obj)
	{
		boolean isEqual = false;
		if ((null != obj) && (obj.getClass() == this.getClass()))
		{
			PathLocation other = (PathLocation)obj;
			isEqual = _location.equals(other._location);
		}
		return isEqual;
	}

	@Override
	public int hashCode()
	{
		return _location.hashCode();
	}

	@Override
	public String toString()
	{
		return String.format("PathLocation(%s)", _location);
	}
}
