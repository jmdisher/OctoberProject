package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IByteLookup;
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
	 * Called to perform a batch update on the lighting model.  This allows multiple light sources to added or removed
	 * in a single call.
	 * Note that this assumes all toAdd and toRemove locations have already been updated with their new values.
	 * All changes are made via the overlay interface.
	 * 
	 * @param overlay Opaque abstraction over the lighting data.
	 * @param toAdd The list of light sources to add.
	 * @param toRemove The list of light sources to remove.
	 */
	public static void batchProcessLight(IBlockDataOverlay overlay
			, List<Light> toAdd
			, List<Light> toRemove
	)
	{
		// We will build a light queue and start it with our list of sources to add.
		// We will add the "re-flood" sources to this queue, afterward, since they are likely lower values (avoids redundant light changes).
		Queue<_Step> lightQueue = new LinkedList<>();
		for (Light add : toAdd)
		{
			_enqueueLightNeighbours(overlay, lightQueue, add.location, add.level);
		}
		
		// First, we want to process all the removed light sources.
		Map<AbsoluteLocation, Byte> reFlood = new HashMap<>();
		Queue<_Step> darkQueue = new LinkedList<>();
		for (Light remove : toRemove)
		{
			_enqueueDarkNeighbours(overlay, darkQueue, reFlood, remove.location, remove.level);
		}
		_runDarkQueue(overlay, darkQueue, reFlood);
		
		// We can now add the re-flood values to the light queue and then run it as a standard light flood.
		for (Map.Entry<AbsoluteLocation, Byte> elt : reFlood.entrySet())
		{
			AbsoluteLocation location = elt.getKey();
			byte value = elt.getValue();
			_enqueueLightNeighbours(overlay, lightQueue, location, value);
		}
		
		// Finally, since all light increasing operations are enqueued, run them.
		_runLightQueue(overlay, lightQueue);
	}


	private static void _runLightQueue(IBlockDataOverlay overlay
			, Queue<_Step> queue
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
			_enqueueLightNeighbours(overlay, queue, location, light);
		}
	}

	private static void _enqueueLightNeighbours(IBlockDataOverlay overlay
			, Queue<_Step> queue
			, AbsoluteLocation start
			, byte light
	)
	{
		_checkLightNeighbour(overlay, queue, start.getRelative(0, 0, -1), light);
		_checkLightNeighbour(overlay, queue, start.getRelative(0, 0,  1), light);
		_checkLightNeighbour(overlay, queue, start.getRelative(0, -1, 0), light);
		_checkLightNeighbour(overlay, queue, start.getRelative(0,  1, 0), light);
		_checkLightNeighbour(overlay, queue, start.getRelative(-1, 0, 0), light);
		_checkLightNeighbour(overlay, queue, start.getRelative( 1, 0, 0), light);
	}

	private static void _checkLightNeighbour(IBlockDataOverlay overlay
			, Queue<_Step> queue
			, AbsoluteLocation location
			, byte lightEntering
	)
	{
		byte previous = overlay.getLight(location);
		if (IByteLookup.NOT_FOUND != previous)
		{
			byte opacity = overlay.getOpacity(location);
			// We must have found this and it must have positive opacity.
			Assert.assertTrue(opacity > 0);
			byte light = (byte) (lightEntering - opacity);
			if (light > previous)
			{
				// The block light can never be negative.
				Assert.assertTrue(light > 0);
				
				// Add this to our map of updates and enqueue it for neighbour processing.
				overlay.setLight(location, light);
				// See if the neighbours need to be lit.
				if (light > 1)
				{
					queue.add(new _Step(location, light));
				}
			}
		}
	}

	private static void _runDarkQueue(IBlockDataOverlay overlay
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> reFlood
	)
	{
		while (!queue.isEmpty())
		{
			_Step next = queue.remove();
			
			// We already checked this so add it to the updates and check its neighbours.
			AbsoluteLocation location = next.location;
			byte light = next.lightValue;
			// This is only in the queue if it could illuminate something else.
			Assert.assertTrue(light > 0);
			_enqueueDarkNeighbours(overlay, queue, reFlood, location, light);
		}
	}

	private static void _enqueueDarkNeighbours(IBlockDataOverlay overlay
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> reFlood
			, AbsoluteLocation start
			, byte light
	)
	{
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative(0, 0, -1), light);
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative(0, 0,  1), light);
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative(0, -1, 0), light);
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative(0,  1, 0), light);
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative(-1, 0, 0), light);
		_checkDarkNeighbour(overlay, queue, reFlood, start.getRelative( 1, 0, 0), light);
	}

	private static void _checkDarkNeighbour(IBlockDataOverlay overlay
			, Queue<_Step> queue
			, Map<AbsoluteLocation, Byte> reFlood
			, AbsoluteLocation location
			, byte lightEntering
	)
	{
		// We only want to operate on this if is has a light value.
		// Note that the caller should make sure that previously darkened blocks return 0 in lightLookup.
		byte existingLight = overlay.getLight(location);
		if (existingLight > 0)
		{
			// Check the light level:
			// -if it matches the expected value, set it to zero and enqueue it (since this means we were lighting it - others could, too)
			// -if it is greater than the expected value, add it to the reflood set (since this is beyond the boundary of what we lit)
			// -cannot be less than expected
			byte opacity = overlay.getOpacity(location);
			// We must have found this and it must have positive opacity.
			Assert.assertTrue(opacity > 0);
			byte light = (byte) (lightEntering - opacity);
			if (existingLight == light)
			{
				// Add this to our set of darkened updates and enqueue it for neighbour processing.
				overlay.setDark(location);
				// See if the neighbours may be darkened by this path
				if (light > 1)
				{
					queue.add(new _Step(location, light));
					
					// NOTE:  If this turns out to be a light source, just set its value and add it to the reflood set.
					byte sourceLight = overlay.getLightSource(location);
					if (sourceLight > 0)
					{
						overlay.setLight(location, sourceLight);
						reFlood.put(location, sourceLight);
					}
					else
					{
						// We may have decided to reflood this via a different path but changed our mind.
						reFlood.remove(location);
					}
				}
			}
			// Make sure that we didn't already flag this as a source or something.
			else if ((existingLight > 1) && !reFlood.containsKey(location))
			{
				// NOTE:  This if statement SHOULD always be true but may not be as a result of unloaded cuboid boundaries or if lighting/opacity configuration changed under an existing world.
				if (existingLight > light)
				{
					// We need to re-flood this one.
					reFlood.put(location, existingLight);
				}
				else
				{
					// We will log this since it might imply a problem (or at least explains any lighting errors to the user).
					System.err.println("WARNING:  Lighting value higher than expected (probably load boundary or config change):  " + location);
				}
			}
		}
	}


	public static record Light(AbsoluteLocation location, byte level)
	{}

	private static record _Step(AbsoluteLocation location, byte lightValue)
	{}

	public interface IBlockDataOverlay
	{
		public byte getLight(AbsoluteLocation location);
		public void setLight(AbsoluteLocation location, byte value);
		public void setDark(AbsoluteLocation location);
		public byte getOpacity(AbsoluteLocation location);
		public byte getLightSource(AbsoluteLocation location);
	}
}
