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
		// We assume that the starting block has already been set.
		Assert.assertTrue(lightLookup.lookup(start) == startLevel);
		
		// We want to intercept lighting lookups to use any updated values we have.
		_BlockDataOverlay overlay = new _BlockDataOverlay(lightLookup, opacityLookup);
		
		// Enqueue the surrounding blocks.
		Queue<_Step> queue = new LinkedList<>();
		_enqueueLightNeighbours(overlay, queue, start, startLevel);
		_runLightQueue(overlay, queue);
		
		return overlay.getChangedValues();
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
		// We assume that the starting block has already been cleared.
		Assert.assertTrue(lightLookup.lookup(start) == 0);
		
		// We want to intercept lighting lookups to use any updated values we have.
		_BlockDataOverlay overlay = new _BlockDataOverlay(lightLookup, opacityLookup);
		
		// Enqueue the surrounding blocks.
		Map<AbsoluteLocation, Byte> reFlood = new HashMap<>();
		Queue<_Step> queue = new LinkedList<>();
		_enqueueDarkNeighbours(overlay, queue, reFlood, start, previousLevel);
		_runDarkQueue(overlay, queue, reFlood);
		
		// Now, re-flood from all of those in the flood set, adding back new updates, over-writing whatever is there.
		// NOTE:  We can re-use the existing overlay object here.
		if (!reFlood.isEmpty())
		{
			Queue<_Step> reQueue = new LinkedList<>();
			for (Map.Entry<AbsoluteLocation, Byte> elt : reFlood.entrySet())
			{
				AbsoluteLocation location = elt.getKey();
				byte value = elt.getValue();
				_enqueueLightNeighbours(overlay, reQueue, location, value);
			}
			_runLightQueue(overlay, reQueue);
		}
		
		return overlay.getChangedValues();
	}


	private static void _runLightQueue(_BlockDataOverlay overlay
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

	private static void _enqueueLightNeighbours(_BlockDataOverlay overlay
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

	private static void _checkLightNeighbour(_BlockDataOverlay overlay
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

	private static void _runDarkQueue(_BlockDataOverlay overlay
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

	private static void _enqueueDarkNeighbours(_BlockDataOverlay overlay
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

	private static void _checkDarkNeighbour(_BlockDataOverlay overlay
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
					// We may have decided to reflood this via a different path but changed our mind.
					reFlood.remove(location);
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

	private static class _BlockDataOverlay
	{
		private final IByteLookup _lightLookup;
		private final IByteLookup _opacityLookup;
		private final Map<AbsoluteLocation, Byte> _changedValues;
		
		public _BlockDataOverlay(IByteLookup lightLookup, IByteLookup opacityLookup)
		{
			_lightLookup = lightLookup;
			_opacityLookup = opacityLookup;
			_changedValues = new HashMap<>();
		}
		public byte getLight(AbsoluteLocation location)
		{
			return _changedValues.containsKey(location)
					? _changedValues.get(location)
					: _lightLookup.lookup(location)
			;
		}
		public void setLight(AbsoluteLocation location, byte value)
		{
			Byte previous = _changedValues.put(location, value);
			// This entry-point can only increase brightness.
			if (null != previous)
			{
				Assert.assertTrue(value > previous);
			}
		}
		public void setDark(AbsoluteLocation location)
		{
			// We expect not to see this happen redundantly.
			Assert.assertTrue(null == _changedValues.put(location, (byte)0));
		}
		public byte getOpacity(AbsoluteLocation location)
		{
			return _opacityLookup.lookup(location);
		}
		public Map<AbsoluteLocation, Byte> getChangedValues()
		{
			return _changedValues;
		}
	}
}
