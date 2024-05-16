package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * A utility class to find the distance between 2 EntityLocations in 3D space.  The distance is computed as the 3D
 * Manhattan distance (that is, only along the cardinal directions, at right angles - no diagonals).
 * The implementation considers the following interpretations of the XYZ spaces:
 * -x is West-East, such that -x is West, +x is East
 * -y is South-North, such that -y is South, +y is North
 * -z is down-up, such that -z is down, +z is up
 * -the XYZ location where an entity "is" is the air block where their feet exist.
 * This means that the .0 location of any single block is considered to be the bottom, South-West corner.
 */
public class PathFinder
{
	// TODO:  Properly plumb in the environment value when we start using this instead of this hard-coded constant.
	public static final short AIR_NUMBER = 0;
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

	public static List<AbsoluteLocation> findPath(Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation source, EntityLocation target)
	{
		// We will limit algorithm by a limit of 2x the manhattan distance, so we don't walk too far around obstacles.
		float manhattan = Math.abs(source.x() - target.x())
				+ Math.abs(source.y() - target.y())
				+ Math.abs(source.z() - target.z())
		;
		float limit = 2 * manhattan;
		return _findPathWithLimit(blockTypeReader, volume, source, target, limit);
	}

	public static List<AbsoluteLocation> findPathWithLimit(Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation source, EntityLocation target, float limitSteps)
	{
		return _findPathWithLimit(blockTypeReader, volume, source, target, limitSteps);
	}

	/**
	 * Finds all locations reachable within the given limitSteps movement cost, returning them as a map of backward
	 * steps (that is, a key is the destination and the value is the step prior to it).
	 * Note that not all target places may make sense since they only also include places like "jumping into the air" as
	 * a final step.
	 * 
	 * @param blockTypeReader Resolves block types.
	 * @param volume The volume of the entity (must be less than 1.0f width).
	 * @param source The starting location of the entity.
	 * @param limitSteps The movement cost limit.
	 * @return The walk-back map.
	 */
	public static Map<AbsoluteLocation, AbsoluteLocation> findPlacesWithinLimit(Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation source, float limitSteps)
	{
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map
		_populateWalkbackMap(walkBackward, blockTypeReader, volume, source, null, limitSteps);
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

	private static List<AbsoluteLocation> _findPathWithLimit(Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation entitySource, EntityLocation entityTarget, float limit)
	{
		// Key is destination.
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map and return the final spot for entityTarget, assuming it could be reached.
		Spot targetSpot = _populateWalkbackMap(walkBackward, blockTypeReader, volume, entitySource, entityTarget, limit);
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

	private static Spot _populateWalkbackMap(Map<AbsoluteLocation, AbsoluteLocation> walkBackward, Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation entitySource, EntityLocation entityTarget, float limit)
	{
		// This algorithm currently only works for 1-block-wide entities..
		Assert.assertTrue(volume.width() < 1.0f);
		int height = Math.round(volume.height() + 0.49f);
		
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
				boolean isStandingOnAir = (AIR_NUMBER == blockTypeReader.apply(down));
				AbsoluteLocation previousStep = walkBackward.get(spotLocation);
				boolean wasXyStep = (null != previousStep) && (previousStep.z() == spotLocation.z());
				boolean didStepIntoHole = (isStandingOnAir && wasXyStep);
				
				if (!didStepIntoHole)
				{
					AbsoluteLocation east = spotLocation.getRelative(1, 0, 0);
					AbsoluteLocation west = spotLocation.getRelative(-1, 0, 0);
					AbsoluteLocation north = spotLocation.getRelative(0, 1, 0);
					AbsoluteLocation south = spotLocation.getRelative(0, -1, 0);
					
					_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, east, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, west, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, north, COST_STEP_FLAT);
					_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, south, COST_STEP_FLAT);
				}
				if (!isStandingOnAir)
				{
					AbsoluteLocation up = spotLocation.getRelative(0, 0, 1);
					_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, up, COST_CLIMB);
				}
				_tryAddSpot(walkBackward, workQueue, blockTypeReader, limit, height, spot, down, COST_FALL);
			}
		}
		return targetSpot;
	}


	private static void _tryAddSpot(Map<AbsoluteLocation, AbsoluteLocation> walkBackward, PriorityQueue<Spot> workQueue, Function<AbsoluteLocation, Short> blockTypeReader, float limit, int height, Spot start, AbsoluteLocation target, float scoreToAdd)
	{
		// Make sure that we haven't already reached this desintation via an earlier path.
		if (!walkBackward.containsKey(target))
		{
			// Make sure that we can fit here.
			if (_canFitInSpace(blockTypeReader, target, height))
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

	private static boolean _canFitInSpace(Function<AbsoluteLocation, Short> blockTypeReader, AbsoluteLocation space, int entityHeight)
	{
		// We just check to see if this block, and the blocks required to accommodate our height, are air.
		// (this will still return true, even if the spot is floating int he air).
		boolean canFit = true;
		for (int i = 0; canFit && (i < entityHeight); ++i)
		{
			AbsoluteLocation check = space.getRelative(0, 0, i);
			canFit = (AIR_NUMBER == blockTypeReader.apply(check));
		}
		return canFit;
	}


	private record Spot(AbsoluteLocation location, float distance)
	{
	}
}
