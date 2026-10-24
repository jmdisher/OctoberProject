package com.jeffdisher.october.pathing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

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
	 * Finds the path, in block locations, of every step from source to target which can be occupied by checking the
	 * blockKind function, limiting the search distance to the given limit of steps.
	 * 
	 * @param navigator The navigator helper.
	 * @param source The source location of the entity.
	 * @param target The target location of the entity.
	 * @return The path to follow to reach the target, starting with the current location, null if no path exists.
	 */
	public static <T extends ASpot> List<AbsoluteLocation> findPathWithLimit(PathFinder.INavigator<T> navigator, EntityLocation source, EntityLocation target)
	{
		return _findPathWithLimit(navigator, source, target);
	}

	/**
	 * Finds all locations reachable within the given limitSteps movement cost, returning them as a map of backward
	 * steps (that is, a key is the destination and the value is the step prior to it).
	 * Note that not all target places may make sense since they only also include places like "jumping into the air" as
	 * a final step.
	 * 
	 * @param navigator The navigator helper.
	 * @param source The starting location of the entity.
	 * @return The walk-back map.
	 */
	public static <T extends ASpot> Map<AbsoluteLocation, AbsoluteLocation> findPlacesWithinLimit(PathFinder.INavigator<T> navigator
		, EntityLocation source
	)
	{
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map
		_populateWalkbackMap(walkBackward
			, navigator
			, source
			, null
		);
		return walkBackward;
	}


	private static <T extends ASpot> List<AbsoluteLocation> _findPathWithLimit(PathFinder.INavigator<T> navigator, EntityLocation entitySource, EntityLocation entityTarget)
	{
		// Key is destination.
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		// This will populate the walkBackward map and return the final spot for entityTarget, assuming it could be reached.
		T targetSpot = _populateWalkbackMap(walkBackward
			, navigator
			, entitySource
			, entityTarget
		);
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

	private static <T extends ASpot> T _populateWalkbackMap(Map<AbsoluteLocation, AbsoluteLocation> walkBackward
		, PathFinder.INavigator<T> navigator
		, EntityLocation entitySource
		, EntityLocation entityTarget
	)
	{
		AbsoluteLocation target = (null != entityTarget) ? entityTarget.getBlockLocation() : null;
		
		// Key is destination.
		PriorityQueue<T> workQueue = new PriorityQueue<>((T one, T two) -> {
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
		T startSpot = navigator.startSpot(entitySource);
		if (null != startSpot)
		{
			AbsoluteLocation old = walkBackward.put(startSpot.location, null);
			// This was empty.
			Assert.assertTrue(null == old);
			workQueue.add(startSpot);
		}
		
		T targetSpot = null;
		while ((null == targetSpot) && !workQueue.isEmpty())
		{
			T spot = workQueue.remove();
			// If this is the target, we are done (note that target can be null if we are just building the map).
			if (spot.location.equals(target))
			{
				targetSpot = spot;
			}
			else
			{
				// We aren't yet at the target so check the spaces around us.
				AbsoluteLocation spotLocation = spot.location;
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(-1, 0, 0));
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(1, 0, 0));
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(0, -1, 0));
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(0, 1, 0));
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(0, 0, -1));
				_addIfValid(walkBackward, workQueue, navigator, spot, spotLocation.getRelative(0, 0, 1));
			}
		}
		return targetSpot;
	}


	private static <T extends ASpot> void _addIfValid(Map<AbsoluteLocation, AbsoluteLocation> walkBackward
		, PriorityQueue<T> workQueue
		, PathFinder.INavigator<T> navigator
		, T previous
		, AbsoluteLocation location
	)
	{
		// Make sure that we haven't already reached this destination via an earlier path.
		if (!walkBackward.containsKey(location))
		{
			T thisSpot = navigator.enterSpot(previous, location);
			if (null != thisSpot)
			{
				AbsoluteLocation old = walkBackward.put(location, previous.location);
				// This was checked above.
				Assert.assertTrue(null == old);
				workQueue.add(thisSpot);
			}
		}
	}


	/**
	 * This class should probably be an interface but the algorithm needs to directly reach into fetch the distance so
	 * this just avoids a bit of duplication (we could convert it later if implementations become more complicated and
	 * if the performance is similar).
	 */
	public abstract static class ASpot
	{
		public final AbsoluteLocation location;
		public final float distance;
		protected ASpot(AbsoluteLocation location, float distance)
		{
			this.location = location;
			this.distance = distance;
		}
	}

	/**
	 * The interface used to determine how a specific navigator type can enter a block.
	 * 
	 * @param <T> The ASpot type.
	 */
	public static interface INavigator <T extends ASpot>
	{
		/**
		 * Creates a new ASpot instance if this navigator can start with an entity based at entityBase.
		 * 
		 * @param entityBase The starting base location of the entity.
		 * @return The new ASpot instance for location, or null if the navigator cannot place an entity at entityBase.
		 */
		T startSpot(EntityLocation entityBase);
		/**
		 * Creates a new ASpot instance if this navigator can enter the block at location from "from".
		 * 
		 * @param from An existing ASpot the navigator is moving from (never null).
		 * @param location The location to navigate into.
		 * @return The new ASpot instance for location, or null if the navigator cannot enter location.
		 */
		T enterSpot(T from, AbsoluteLocation location);
	}
}
