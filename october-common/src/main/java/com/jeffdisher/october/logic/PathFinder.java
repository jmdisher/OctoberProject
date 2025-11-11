package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * A utility class to find the distance between 2 EntityLocations in 3D space.  The distance is computed as the 3D
 * Manhattan distance (that is, only along the cardinal directions, at right angles - no diagonals).
 * The implementation considers the following interpretations of the XYZ spaces:
 * -x is West-East, such that -x is West, +x is East
 * -y is South-North, such that -y is South, +y is North
 * -z is down-up, such that -z is down, +z is up
 * -the XYZ location where an entity "is" is the west-south-down-most block in the entity's volume but this is only by
 * convention and the lookup function for block kinds is expected to interpret this location consistently with other
 * EntityLocation values passed in.
 * Of special note is that this implementation doesn't make any assumptions about the volume of the entity walking
 * through the path, meaning that a volume greater than a single block must be reported as a single location in the
 * block kind look-up function.
 */
public class PathFinder
{
	/**
	 * Falling 1 block will still be considered as cost, but a very small one.
	 */
	public static final float COST_FALL = 0.5f;
	/**
	 * Climbing up a block should be considered expensive.
	 */
	public static final float COST_CLIMB = 1.5f;
	/**
	 * Walking on flat ground will be the baseline action.
	 */
	public static final float COST_STEP_FLAT = 1.0f;

	/**
	 * Finds the path, in block locations, of every step from source to target which can be occupied by checking the
	 * blockKind function.
	 * 
	 * @param blockKind Returns the kind of block at the given location.
	 * @param source The source location of the entity.
	 * @param target The target location of the entity.
	 * @return The path to follow to reach the target, starting with the current location, null if no path exists.
	 */
	public static List<AbsoluteLocation> findPath(Function<AbsoluteLocation, BlockKind> blockKind, EntityLocation source, EntityLocation target)
	{
		// We will limit algorithm by a limit of 2x the manhattan distance, so we don't walk too far around obstacles.
		float manhattan = Math.abs(source.x() - target.x())
				+ Math.abs(source.y() - target.y())
				+ Math.abs(source.z() - target.z())
		;
		float limit = 2 * manhattan;
		return _findPathWithLimit(blockKind, source, target, limit);
	}

	/**
	 * Finds the path, in block locations, of every step from source to target which can be occupied by checking the
	 * blockKind function, limiting the search distance to the given limit of steps.
	 * 
	 * @param blockKind Returns the kind of block at the given location.
	 * @param source The source location of the entity.
	 * @param target The target location of the entity.
	 * @param limitSteps The maximum distance which can be travelled, in units of total blocks.
	 * @return The path to follow to reach the target, starting with the current location, null if no path exists.
	 */
	public static List<AbsoluteLocation> findPathWithLimit(Function<AbsoluteLocation, BlockKind> blockKind, EntityLocation source, EntityLocation target, float limitSteps)
	{
		return _findPathWithLimit(blockKind, source, target, limitSteps);
	}

	/**
	 * Finds all locations reachable within the given limitSteps movement cost, returning them as a map of backward
	 * steps (that is, a key is the destination and the value is the step prior to it).
	 * Note that not all target places may make sense since they only also include places like "jumping into the air" as
	 * a final step.
	 * 
	 * @param blockKind Returns the kind of block at the given location.
	 * @param source The starting location of the entity.
	 * @param limitSteps The movement cost limit.
	 * @return The walk-back map.
	 */
	public static Map<AbsoluteLocation, AbsoluteLocation> findPlacesWithinLimit(Function<AbsoluteLocation, BlockKind> blockKind, EntityLocation source, float limitSteps)
	{
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map
		_populateWalkbackMap(walkBackward, blockKind, source, null, limitSteps);
		return walkBackward;
	}


	private static float _getStartingDistance(EntityLocation source, EntityLocation target)
	{
		// We need to translate the source into the relative location of the destination.
		EntityLocation sourceOffset = source.getOffsetIntoBlock();
		EntityLocation targetOffset = target.getOffsetIntoBlock();
		return (targetOffset.x() - sourceOffset.x())
				+ (targetOffset.y() - sourceOffset.y())
				+ (targetOffset.z() - sourceOffset.z())
		;
	}

	private static List<AbsoluteLocation> _findPathWithLimit(Function<AbsoluteLocation, BlockKind> blockKind, EntityLocation entitySource, EntityLocation entityTarget, float limit)
	{
		// Key is destination.
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map and return the final spot for entityTarget, assuming it could be reached.
		Spot targetSpot = _populateWalkbackMap(walkBackward, blockKind, entitySource, entityTarget, limit);
		List<AbsoluteLocation> path = null;
		if (null != targetSpot)
		{
			// We found the target so find the path.
			path = new ArrayList<>();
			AbsoluteLocation back = targetSpot.location;
			while (null != back)
			{
				path.add(0, back);
				back = walkBackward.get(back);
			}
		}
		return path;
	}

	private static Spot _populateWalkbackMap(Map<AbsoluteLocation, AbsoluteLocation> walkBackward, Function<AbsoluteLocation, BlockKind> blockKind, EntityLocation entitySource, EntityLocation entityTarget, float limit)
	{
		AbsoluteLocation start = entitySource.getBlockLocation();
		AbsoluteLocation target = (null != entityTarget) ? entityTarget.getBlockLocation() : null;
		
		float initialDistance = (null != target) ? _getStartingDistance(entitySource, entityTarget): 0.0f;
		
		// Key is destination.
		PriorityQueue<Spot> workQueue = new PriorityQueue<>((Spot one, Spot two) -> {
			float difference = one.distance - two.distance;
			int signum = 0;
			if (difference < 0.0f)
			{
				signum = -1;
			}
			else if (difference > 0.0f)
			{
				signum = 1;
			}
			return signum;
		});
		walkBackward.put(start, null);
		workQueue.add(new Spot(start, initialDistance));
		Spot targetSpot = null;
		while ((null == targetSpot) && !workQueue.isEmpty())
		{
			Spot spot = workQueue.remove();
			// If this is the target, we are done (note that target can be null if we are just building the map).
			if (spot.location.equals(target))
			{
				targetSpot = spot;
			}
			else
			{
				// We aren't yet at the target so check the spaces around us.
				AbsoluteLocation spotLocation = spot.location;
				// We will just check all 6 directions around our current spot with a few caveats:
				// -NEVER check anything already in our walked set, since we already reached that via a shorter path (since this is a priority queue).
				// -don't bother checking "up" if we are standing on air since we would have nothing from which to "jump up".
				
				AbsoluteLocation down = spotLocation.getRelative(0, 0, -1);
				
				// If we are standing on an air block AND the previous step was only XY movement, that means that we
				// just stepped into a hole so we can't immediately step out (can in later steps but we can't step right
				// over a gap).
				// (we just check the down block for solid, assuming anything walkabe 
				boolean isStandingOnAir = (BlockKind.SOLID != blockKind.apply(down));
				boolean isSwimmable = (BlockKind.SWIMMABLE == blockKind.apply(spotLocation));
				AbsoluteLocation previousStep = walkBackward.get(spotLocation);
				boolean wasXyStep = (null != previousStep) && (previousStep.z() == spotLocation.z());
				boolean didStepIntoHole = (isStandingOnAir && wasXyStep);
				
				// If we are currently in swimmable block, that means we don't need to fall into a hole.
				if (!didStepIntoHole || isSwimmable)
				{
					AbsoluteLocation east = spotLocation.getRelative(1, 0, 0);
					AbsoluteLocation west = spotLocation.getRelative(-1, 0, 0);
					AbsoluteLocation north = spotLocation.getRelative(0, 1, 0);
					AbsoluteLocation south = spotLocation.getRelative(0, -1, 0);
					
					_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, east, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, west, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, north, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, south, COST_STEP_FLAT);
				}
				// If we are currently in swimmable block, that means we can still "jump" (swim) up.
				if (!isStandingOnAir || isSwimmable)
				{
					AbsoluteLocation up = spotLocation.getRelative(0, 0, 1);
					_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, up, COST_CLIMB);
				}
				_tryAddSpot(walkBackward, workQueue, blockKind, limit, spot, down, COST_FALL);
			}
		}
		return targetSpot;
	}


	private static void _tryAddSpot(Map<AbsoluteLocation, AbsoluteLocation> walkBackward, PriorityQueue<Spot> workQueue, Function<AbsoluteLocation, BlockKind> blockKind, float limit, Spot start, AbsoluteLocation target, float scoreToAdd)
	{
		// Make sure that we haven't already reached this desintation via an earlier path.
		if (!walkBackward.containsKey(target))
		{
			// Make sure that we can fit here.
			if (BlockKind.SOLID != blockKind.apply(target))
			{
				// Add this target spot unless we have gone too far.
				float newDistance = start.distance + scoreToAdd;
				if (newDistance <= limit)
				{
					Spot newStep = new Spot(target, newDistance);
					AbsoluteLocation old = walkBackward.put(target, start.location);
					// This was checked above.
					Assert.assertTrue(null == old);
					workQueue.add(newStep);
				}
			}
		}
	}


	public static enum BlockKind
	{
		/**
		 * A block which can be traversed easily.
		 */
		WALKABLE,
		/**
		 * A block which cannot be passed through and which supports an entity.
		 */
		SOLID,
		/**
		 * A block which is "WALKABLE" but also allows swimming (and is not breathable).
		 */
		SWIMMABLE,
	}

	private record Spot(AbsoluteLocation location, float distance)
	{
	}
}
