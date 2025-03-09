package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * Helper functions related to ray-casting:  Finding the intersection of a line in space with the block world.
 */
public class RayCastHelpers
{
	/**
	 * Essentially a 3D digital differential analysis (DDA) algorithm.  It will walk the ray from start to end until
	 * stopPredicate returns true, returning a record of the last 2 blocks (null if it never return true).
	 * The point of the algorithm is to walk the locations the ray passes through, in-order.
	 * At a minimum, this will call stopPredicate once (if start/end are in the same location).
	 * 
	 * @param start The starting-point of the ray.
	 * @param end The end-point of the ray.
	 * @param stopPredicate Returns true when the search should stop.
	 * @return The result containing the stop block and previous block or null, if stopPredicate never returned true.
	 */
	public static RayBlock findFirstCollision(EntityLocation start, EntityLocation end, Predicate<AbsoluteLocation> stopPredicate)
	{
		_ResultBuilder<RayBlock> builder = (boolean wasStopped
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, Axis collisionAxis
				, float rayDistance
		) -> {
			return wasStopped
					? new RayBlock(stopBlock, preStopBlock, collisionAxis, rayDistance)
					: null
			;
		};
		return _findFirstCollision(builder, start, end, stopPredicate);
	}

	/**
	 * Runs the ray-casting collision algorithm on all points defining this base and volume, returning the final
	 * location of the volume moving with velocity.
	 * 
	 * @param base The bottom-south-west point defining the bounds of this entity.
	 * @param volume The volume of the entity.
	 * @param velocity The velocity vector of the entity.
	 * @param stopPredicate Returns true if the block at the given location should be considered a collision.
	 * @return A description of the final entity location (never null but axis may be null if it never collided with
	 * anything).
	 */
	public static RayMovement applyMovement(EntityLocation base, EntityVolume volume, EntityLocation velocity, Predicate<AbsoluteLocation> stopPredicate)
	{
		float vX = velocity.x();
		float vY = velocity.y();
		float vZ = velocity.z();
		EntityLocation[] points = new EntityLocation[] {
				base,
				new EntityLocation(base.x(), base.y(), base.z() + volume.height()),
				new EntityLocation(base.x(), base.y() + volume.width(), base.z()),
				new EntityLocation(base.x(), base.y() + volume.width(), base.z() + volume.height()),
				new EntityLocation(base.x() + volume.width(), base.y(), base.z()),
				new EntityLocation(base.x() + volume.width(), base.y(), base.z() + volume.height()),
				new EntityLocation(base.x() + volume.width(), base.y() + volume.width(), base.z()),
				new EntityLocation(base.x() + volume.width(), base.y() + volume.width(), base.z() + volume.height()),
		};
		_ResultBuilder<RayBlock> builder = (boolean wasStopped
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, Axis collisionAxis
				, float rayDistance
		) -> {
			return wasStopped
					? new RayBlock(stopBlock, preStopBlock, collisionAxis, rayDistance)
					: null
			;
		};
		
		float rayLength = (float)Math.sqrt(vX * vX + vY * vY + vZ * vZ);
		float distance = rayLength;
		Axis axis = null;
		for (int i = 0; (distance > 0.0f) && (i < points.length); ++i)
		{
			EntityLocation point = points[i];
			EntityLocation edge = new EntityLocation(point.x() + vX, point.y() + vY, point.z() + vZ);
			RayBlock result = _findFirstCollision(builder, point, edge, stopPredicate);
			if (null != result)
			{
				float one = result.rayDistance;
				if (one < distance)
				{
					distance = one;
					axis = result.collisionAxis;
				}
			}
		}
		
		EntityLocation finalState;
		if (distance > 0.0f)
		{
			float multiplier = distance / rayLength;
			float finalX = vX * multiplier;
			float finalY = vY * multiplier;
			float finalZ = vZ * multiplier;
			
			if (distance > 0.0f)
			{
				finalState = new EntityLocation(base.x() + finalX, base.y() + finalY, base.z() + finalZ);
			}
			else
			{
				finalState = base;
			}
		}
		else
		{
			finalState = base;
		}
		return new RayMovement(finalState, axis, distance);
	}

	public static List<AbsoluteLocation> findFullLine(AbsoluteLocation start, AbsoluteLocation end)
	{
		_ResultBuilder<List<AbsoluteLocation>> builder = (boolean wasStopped
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, Axis collisionAxis
				, float rayDistance
		) -> {
			return path;
		};
		EntityLocation floatStart = start.toEntityLocation();
		EntityLocation floatEnd = end.toEntityLocation();
		Predicate<AbsoluteLocation> stopPredicate = (AbsoluteLocation location) -> false;
		return _findFirstCollision(builder, floatStart, floatEnd, stopPredicate);
	}


	private static <T> T _findFirstCollision(_ResultBuilder<T> builder, EntityLocation start, EntityLocation end, Predicate<AbsoluteLocation> stopPredicate)
	{
		AbsoluteLocation startBlock = start.getBlockLocation();
		AbsoluteLocation endBlock = end.getBlockLocation();
		
		// Get the components of the vector in all 3 dimensions.
		float rayX = end.x() - start.x();
		float rayY = end.y() - start.y();
		float rayZ = end.z() - start.z();
		
		// Derive the length of the vector travelled between faces in all 3 dimensions.
		float deltaX = Math.abs(1.0f / rayX);
		float deltaY = Math.abs(1.0f / rayY);
		float deltaZ = Math.abs(1.0f / rayZ);
		
		// Get the distance from the start to the nearest face, in all 3 dimensions.
		float insetX = start.x() - (float)Math.floor(start.x());
		float insetY = start.y() - (float)Math.floor(start.y());
		float insetZ = start.z() - (float)Math.floor(start.z());
		
		// Determine the starting offset to each face.
		float firstX = (rayX < 0.0f) ? insetX : (1.0f - insetX);
		float firstY = (rayY < 0.0f) ? insetY : (1.0f - insetY);
		float firstZ = (rayZ < 0.0f) ? insetZ : (1.0f - insetZ);
		
		// Determine the magnitude of each starting step, based on reaching the nearest face.
		float stepX = firstX * deltaX;
		float stepY = firstY * deltaY;
		float stepZ = firstZ * deltaZ;
		
		// Determine the corresponding logical step directions.
		int dirX = (rayX > 0.0f) ? 1 : -1;
		int dirY = (rayY > 0.0f) ? 1 : -1;
		int dirZ = (rayZ > 0.0f) ? 1 : -1;
		
		// We want to track how far along each axis we walked, in order to calculate the ray length to collision.
		float distanceX = firstX;
		float distanceY = firstY;
		float distanceZ = firstZ;
		
		List<AbsoluteLocation> path = new ArrayList<>();
		AbsoluteLocation thisStep = startBlock;
		AbsoluteLocation lastFalse = null;
		Axis axis = null;
		boolean stop = stopPredicate.test(thisStep);
		while (!stop && !thisStep.equals(endBlock))
		{
			path.add(thisStep);
			lastFalse = thisStep;
			if (null != axis)
			{
				switch (axis)
				{
					case X:
						distanceX += 1.0f;
						break;
					case Y:
						distanceY += 1.0f;
						break;
					case Z:
						distanceZ += 1.0f;
						break;
				}
			}
			
			if (stepX < stepY)
			{
				// Y is not the smallest.
				// See which is smaller, the X or Z.
				if (stepX < stepZ)
				{
					axis = Axis.X;
					thisStep = thisStep.getRelative(dirX, 0, 0);
					if (endBlock.x() == thisStep.x())
					{
						stepX = Float.MAX_VALUE;
					}
					else
					{
						stepX += deltaX;
					}
				}
				else
				{
					axis = Axis.Z;
					thisStep = thisStep.getRelative(0, 0, dirZ);
					if (endBlock.z() == thisStep.z())
					{
						stepZ = Float.MAX_VALUE;
					}
					else
					{
						stepZ += deltaZ;
					}
				}
			}
			else
			{
				// X is not the smallest.
				// See which is smaller, the Y or Z.
				if (stepY < stepZ)
				{
					axis = Axis.Y;
					thisStep = thisStep.getRelative(0, dirY, 0);
					if (endBlock.y() == thisStep.y())
					{
						stepY = Float.MAX_VALUE;
					}
					else
					{
						stepY += deltaY;
					}
				}
				else
				{
					axis = Axis.Z;
					thisStep = thisStep.getRelative(0, 0, dirZ);
					if (endBlock.z() == thisStep.z())
					{
						stepZ = Float.MAX_VALUE;
					}
					else
					{
						stepZ += deltaZ;
					}
				}
			}
			
			stop = stopPredicate.test(thisStep);
		}
		path.add(thisStep);
		
		float rayDistance;
		if (null != axis)
		{
			float rayLength = (float)Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
			switch(axis)
			{
			case X:
				rayDistance = distanceX * rayLength / Math.abs(rayX);
				break;
			case Y:
				rayDistance = distanceY * rayLength / Math.abs(rayY);
				break;
			case Z:
				rayDistance = distanceZ * rayLength / Math.abs(rayZ);
				break;
			default:
				throw Assert.unreachable();
			}
		}
		else
		{
			rayDistance = 0.0f;
		}
		return builder.build(stop, path, thisStep, lastFalse, axis, rayDistance);
	}


	public static record RayBlock(AbsoluteLocation stopBlock
			, AbsoluteLocation preStopBlock
			, Axis collisionAxis
			, float rayDistance
	) {}

	public static record RayMovement(EntityLocation location
			, Axis collisionAxis
			, float rayDistance
	) {}

	public static enum Axis { X, Y, Z,}

	private static interface _ResultBuilder<T>
	{
		T build(boolean wasStopped
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, Axis collisionAxis
				, float rayDistance
		);
	}
}
