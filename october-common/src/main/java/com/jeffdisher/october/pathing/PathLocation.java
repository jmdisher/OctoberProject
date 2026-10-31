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
	 * The edge size of a single instance (1.0f would mean 1-to-1 with other units).
	 */
	public static final float PATH_UNIT = 1.0f;

	/**
	 * Creates an instance based on the west-south-bottom corner of the given location.
	 * 
	 * @param location The absolute location to use.
	 * @return A new instance.
	 */
	public static PathLocation forBlock(AbsoluteLocation location)
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
		return Math.abs(from._location.x() - to._location.x())
			+ Math.abs(from._location.y() - to._location.y())
			+ Math.abs(from._location.z() - to._location.z())
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
		
		if (from._location.x() > to._location.x())
		{
			direction = FacingDirection.WEST;
		}
		else if (from._location.x() < to._location.x())
		{
			direction = FacingDirection.EAST;
		}
		if (from._location.y() > to._location.y())
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.SOUTH;
		}
		else if (from._location.y() < to._location.y())
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.NORTH;
		}
		if (from._location.z() > to._location.z())
		{
			Assert.assertTrue(null == direction);
			direction = FacingDirection.DOWN;
		}
		else if (from._location.z() < to._location.z())
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
