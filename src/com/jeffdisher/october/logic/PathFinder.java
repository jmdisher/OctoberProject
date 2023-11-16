package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


// Note that this implementation assumes that z is "up/down".
public class PathFinder
{
	public static List<AbsoluteLocation> findPath(Function<AbsoluteLocation, Short> blockTypeReader, EntityVolume volume, EntityLocation source, AbsoluteLocation target)
	{
		// This algorithm currently only works for 1-block-wide entities..
		Assert.assertTrue(volume.width() < 1.0f);
		
		AbsoluteLocation start = source.getLocation();
		int height = Math.round(volume.height() + 0.49f);
		int manhattan = Math.abs(start.x() - target.x())
				+ Math.abs(start.y() - target.y())
				+ Math.abs(start.z() - target.z())
		;
		int limit = 2 * manhattan;
		// Key is destination.
		Map<AbsoluteLocation, AbsoluteLocation> walkBackward = new HashMap<>();
		PriorityQueue<Spot> workQueue = new PriorityQueue<>((Spot one, Spot two) -> Integer.signum(one.distance - two.distance));
		workQueue.add(new Spot(start, 0));
		Spot targetSpot = null;
		while ((null == targetSpot) && !workQueue.isEmpty())
		{
			Spot spot = workQueue.remove();
			// If this is the target, we are done.
			if (target.equals(spot.location))
			{
				targetSpot = spot;
			}
			else
			{
				// Check 12 places:  +/- x/y and +/=/- z.
				// Note that all the z options are checked at the same time.
				// Make sure that we don't check who sent us here.
				AbsoluteLocation spotLocation = spot.location;
				AbsoluteLocation caller = walkBackward.get(spotLocation);
				int deltaX = (null != caller) ? caller.x() - spotLocation.x() : 0;
				int deltaY = (null != caller) ? caller.y() - spotLocation.y() : 0;
				if (-1 != deltaX)
				{
					_checkColumn(walkBackward, workQueue, blockTypeReader, limit, height, spot, spotLocation.getRelative(-1, 0, -2));
				}
				if (1 != deltaX)
				{
					_checkColumn(walkBackward, workQueue, blockTypeReader, limit, height, spot, spotLocation.getRelative(1, 0, -2));
				}
				if (-1 != deltaY)
				{
					_checkColumn(walkBackward, workQueue, blockTypeReader, limit, height, spot, spotLocation.getRelative(0, -1, -2));
				}
				if (1 != deltaY)
				{
					_checkColumn(walkBackward, workQueue, blockTypeReader, limit, height, spot, spotLocation.getRelative(0, 1, -2));
				}
			}
		}
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


	private static void _checkColumn(Map<AbsoluteLocation, AbsoluteLocation> walkBackward, PriorityQueue<Spot> workQueue, Function<AbsoluteLocation, Short> blockTypeReader, int limit, int height, Spot start, AbsoluteLocation base)
	{
		AbsoluteLocation floor = null;
		int contiguousAir = 0;
		AbsoluteLocation check = base;
		// We want to check this spot below the bottom, then the 3 actual locations, and add enough space for height.
		AbsoluteLocation match = null;
		for (int i = 0; (null == match) && (i < (1 + 3 + height)); ++i)
		{
			short blockType = blockTypeReader.apply(check);
			if (BlockAspect.AIR == blockType)
			{
				contiguousAir += 1;
			}
			else
			{
				if ((null != floor) && (contiguousAir >= height))
				{
					// This will work (and nothing else in this column will.
					match = floor.getRelative(0, 0, 1);
				}
				else
				{
					floor = check;
					contiguousAir = 0;
				}
			}
			// Check the next block up.
			check = check.getRelative(0, 0, 1);
		}
		if ((null != floor) && (contiguousAir >= height))
		{
			// We didn't hit the top of the column but we still fit in it.
			match = floor.getRelative(0, 0, 1);
		}
		// The walk order means that the first match is the best one.
		if ((null != match) && !walkBackward.containsKey(match))
		{
			// Check the score.
			int startZ = start.location.z();
			int matchZ = match.z();
			// We will say that jumping is twice the distance but falling and walking isn't.
			int score = (matchZ > startZ)
					? 2
					: 1
			;
			int newDistance = start.distance + score;
			if (newDistance < limit)
			{
				Spot newStep = new Spot(match, newDistance);
				AbsoluteLocation old = walkBackward.put(match, start.location);
				// This was checked above.
				Assert.assertTrue(null == old);
				workQueue.add(newStep);
			}
		}
	}


	private record Spot(AbsoluteLocation location, int distance)
	{
	}
}
