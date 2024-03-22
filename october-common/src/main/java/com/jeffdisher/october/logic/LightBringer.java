package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Utility class to manage light propagation.  The caller is responsible for making sure that this is called at the
 * right time for whatever operation it is performing.
 * Note that the implementation is a breadth-first flood-fill sort of approach.  This also means that light can actually
 * spill around corners (it can solve mazes), which may not be ideal but is our current approach.
 */
public class LightBringer
{
	/**
	 * Called to spread light from a new source which has just been placed in the world (assumes that the start location
	 * already has startLevel light level set in the block).
	 * Note that this doesn't change the underlying data, only returns the changes which must be made.
	 * 
	 * @param lightLookup A function to look up block light levels.
	 * @param opacityLookup A function to look up block opacity.
	 * @param start The starting light source location.
	 * @param startLevel The intensity of the new light source.
	 * @return A map of locations and light levels to update.
	 */
	public static Map<AbsoluteLocation, Byte> spreadLight(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, AbsoluteLocation start
			, byte startLevel
	)
	{
		// We need to have something to do.
		Assert.assertTrue(startLevel > 1);
		Assert.assertTrue(startLevel <= LightAspect.MAX_LIGHT);
		Map<AbsoluteLocation, Byte> updates = new HashMap<>();
		updates.put(start, startLevel);
		
		Queue<_Step> queue = new LinkedList<>();
		
		// We assume that the starting block has already been set.
		Assert.assertTrue(lightLookup.lookup(start) == startLevel);
		
		// Enqueue the surrounding blocks.
		_enqueueLightNeighbours(lightLookup, opacityLookup, queue, updates, start, startLevel);
		
		_runLightQueue(lightLookup, opacityLookup, queue, updates);
		
		// Remove the original since it was just part of a termination condition.
		updates.remove(start);
		return updates;
	}

	/**
	 * Called to remove the light spread from a now-removed source in the world (assumes that the start location already
	 * has its light level set to 0 in the block).  This also takes into account the re-flowing of other light sources
	 * which still remain after removing this one.
	 * Note that this doesn't change the underlying data, only returns the changes which must be made.
	 * 
	 * @param lightLookup A function to look up block light levels.
	 * @param opacityLookup A function to look up block opacity.
	 * @param start The location where the light source was removed.
	 * @param previousLevel The previous value of the light source, prior to its removal.
	 * @return A map of locations and light levels to update.
	 */
	public static Map<AbsoluteLocation, Byte> removeLight(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, AbsoluteLocation start
			, byte previousLevel
	)
	{
		// Removing the light is somewhat more complicated as the operation needs to reverse if it finds a brighter than expected patch so this is 2 phases:
		// -remove the light eminating from this source
		// -re-flood the light from the other sources on the border
		
		// We need to have something to do.
		Assert.assertTrue(previousLevel > 0);
		Map<AbsoluteLocation, Byte> updates = new HashMap<>();
		Map<AbsoluteLocation, Byte> reFlood = new HashMap<>();
		updates.put(start, (byte)0);
		
		Queue<_Step> queue = new LinkedList<>();
		
		// We assume that the starting block has already been cleared.
		Assert.assertTrue(lightLookup.lookup(start) == 0);
		
		// Enqueue the surrounding blocks.
		_enqueueDarkNeighbours(lightLookup, opacityLookup, queue, updates, reFlood, start, previousLevel);
		
		while (!queue.isEmpty())
		{
			_Step next = queue.remove();
			
			// We already checked this so add it to the updates and check its neighbours.
			AbsoluteLocation location = next.location;
			byte light = next.lightValue;
			// This is only in the queue if it could illuminate something else.
			Assert.assertTrue(light > 0);
			_enqueueDarkNeighbours(lightLookup, opacityLookup, queue, updates, reFlood, location, light);
		}
		
		// Remove the original since it was just part of a termination condition.
		updates.remove(start);
		
		// Now, re-flood from all of those in the flood set, adding back new updates, over-writing whatever is there.
		if (!reFlood.isEmpty())
		{
			// We need to make a shim over the lookup mechanism to return values from the previous phase updates.
			IByteLookup localLightLookup = (AbsoluteLocation location) -> {
				byte original = lightLookup.lookup(location);
				return (IByteLookup.NOT_FOUND != original)
						? (updates.containsKey(location) ? updates.get(location) : original)
						: IByteLookup.NOT_FOUND
				;
			};
			
			Map<AbsoluteLocation, Byte> reUpdates = new HashMap<>();
			Queue<_Step> reQueue = new LinkedList<>();
			for (Map.Entry<AbsoluteLocation, Byte> elt : reFlood.entrySet())
			{
				AbsoluteLocation location = elt.getKey();
				byte value = elt.getValue();
				reUpdates.put(location, value);
				_enqueueLightNeighbours(localLightLookup, opacityLookup, reQueue, reUpdates, location, value);
			}
			_runLightQueue(localLightLookup, opacityLookup, reQueue, reUpdates);
			
			// Remove the original since it was just part of a termination condition.
			for (AbsoluteLocation location : reFlood.keySet())
			{
				reUpdates.remove(location);
			}
			
			// Now, write these back over the updates.
			updates.putAll(reUpdates);
		}
		
		return updates;
	}


	private static void _runLightQueue(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> updates
	)
	{
		while (!queue.isEmpty())
		{
			_Step next = queue.remove();
			
			// We already checked this so add it to the updates and check its neighbours.
			AbsoluteLocation location = next.location;
			byte light = next.lightValue;
			// This is only in the queue if it could illuminate something else.
			Assert.assertTrue(light > 1);
			_enqueueLightNeighbours(lightLookup, opacityLookup, queue, updates, location, light);
		}
	}

	private static void _enqueueLightNeighbours(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> updates
			, AbsoluteLocation start
			, byte light
	)
	{
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative(0, 0, -1), light);
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative(0, 0,  1), light);
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative(0, -1, 0), light);
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative(0,  1, 0), light);
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative(-1, 0, 0), light);
		_checkLightNeighbour(lightLookup, opacityLookup, queue, updates, start.getRelative( 1, 0, 0), light);
	}

	private static void _checkLightNeighbour(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> updates
			, AbsoluteLocation location
			, byte lightEntering
	)
	{
		byte previous = lightLookup.lookup(location);
		if (IByteLookup.NOT_FOUND != previous)
		{
			// See if we have already processed this one (only happens for re-illumination or unusual opacity configurations).
			if (updates.containsKey(location))
			{
				previous = updates.get(location);
			}
			byte opacity = opacityLookup.lookup(location);
			// We must have found this and it must have positive opacity.
			Assert.assertTrue(opacity > 0);
			byte light = (byte) (lightEntering - opacity);
			if (light > previous)
			{
				// The block light can never be negative.
				Assert.assertTrue(light > 0);
				
				// Add this to our map of updates and enqueue it for neighbour processing.
				Assert.assertTrue(null == updates.put(location, light));
				// See if the neighbours need to be lit.
				if (light > 1)
				{
					queue.add(new _Step(location, light));
				}
			}
		}
	}

	private static void _enqueueDarkNeighbours(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> updates
			, Map<AbsoluteLocation, Byte> reFlood
			, AbsoluteLocation start
			, byte light
	)
	{
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative(0, 0, -1), light);
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative(0, 0,  1), light);
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative(0, -1, 0), light);
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative(0,  1, 0), light);
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative(-1, 0, 0), light);
		_checkDarkNeighbour(lightLookup, opacityLookup, queue, updates, reFlood, start.getRelative( 1, 0, 0), light);
	}

	private static void _checkDarkNeighbour(IByteLookup lightLookup
			, IByteLookup opacityLookup
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> updates
			, Map<AbsoluteLocation, Byte> reFlood
			, AbsoluteLocation location
			, byte lightEntering
	)
	{
		if (!updates.containsKey(location) && !reFlood.containsKey(location))
		{
			byte existingLight = lightLookup.lookup(location);
			if (IByteLookup.NOT_FOUND != existingLight)
			{
				// Check the light level:
				// -if it matches the expected value, set it to zero and enqueue it
				// -if it is greater than the expected value, add it to the reflood set
				// -cannot be less than expected
				byte opacity = opacityLookup.lookup(location);
				// We must have found this and it must have positive opacity.
				Assert.assertTrue(opacity > 0);
				byte light = (byte) (lightEntering - opacity);
				if ((existingLight == light) && (existingLight > 0))
				{
					// Add this to our map of updates and enqueue it for neighbour processing.
					Assert.assertTrue(null == updates.put(location, (byte)0));
					// See if the neighbours need to be lit.
					if (light > 1)
					{
						queue.add(new _Step(location, light));
					}
				}
				else if (existingLight > 1)
				{
					Assert.assertTrue(existingLight > light);
					// We need to re-flood this one.
					reFlood.put(location, existingLight);
				}
			}
		}
	}


	/**
	 * We use this instead of the generalized Function<> just to avoid gratuitous wrapping/unwrapping.
	 */
	public static interface IByteLookup
	{
		public static final byte NOT_FOUND = -1;
		/**
		 * Looks up the corresponding byte value at the given location, returning -1 if it can't be found.
		 * 
		 * @param location The location to lookup.
		 * @return The byte value at that location or -1 if not found.
		 */
		byte lookup(AbsoluteLocation location);
	}

	private static record _Step(AbsoluteLocation location, byte lightValue)
	{}
}
