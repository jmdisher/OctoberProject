package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
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
		_ResultBuilder<RayBlock> builder = (Axis collisionAxis
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, float rayDistance
		) -> {
			return (null != collisionAxis)
					? new RayBlock(stopBlock, preStopBlock, collisionAxis, rayDistance)
					: null
			;
		};
		return _findFirstCollision(builder, start, end, stopPredicate);
	}

	/**
	 * Finds the sequence of blocks from start to end, inclusive, traced from their bottom-left-south corners.
	 * 
	 * @param start The start block.
	 * @param end The end block.
	 * @return The list of blocks in the sequence they were entered (first element is start and last is end).
	 */
	public static List<AbsoluteLocation> findFullLine(AbsoluteLocation start, AbsoluteLocation end)
	{
		_ResultBuilder<List<AbsoluteLocation>> builder = (Axis collisionAxis
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
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
		if (stop)
		{
			// This is the degenerate case where we collide at the source.
			axis = Axis.INTERNAL;
		}
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
					case INTERNAL:
						throw Assert.unreachable();
					default:
						throw Assert.unreachable();
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
			case INTERNAL:
				rayDistance = 0.0f;
				break;
			default:
				throw Assert.unreachable();
			}
		}
		else
		{
			rayDistance = 0.0f;
		}
		Axis collisionAxis = stop ? axis : null;
		return builder.build(collisionAxis, path, thisStep, lastFalse, rayDistance);
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

	public static enum Axis { X, Y, Z, INTERNAL,}

	private static interface _ResultBuilder<T>
	{
		/**
		 * Returns an implementation-defined result of the ray-cast operation.
		 * 
		 * @param collisionAxis The axis where the ray entered the stopBlock (null if it ended due to distance, not an
		 * explicit stop).
		 * @param path The path of blocks the ray intersected, including the starting block and the final block (whether
		 * explicitly stopped or not).
		 * @param stopBlock The final block evaluated by the ray cast, whether explicitly stopped or not (never null).
		 * @param preStopBlock The location evaluated prior to stopBlock (can be null if only stopBlock was evaluated).
		 * @param rayDistance The total distance the ray extended before collision.
		 * @return The result of the ray cast.
		 */
		T build(Axis collisionAxis
				, List<AbsoluteLocation> path
				, AbsoluteLocation stopBlock
				, AbsoluteLocation preStopBlock
				, float rayDistance
		);
	}
}
