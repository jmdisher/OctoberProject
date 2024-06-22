package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockLogicChange;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;


/**
 * Helpers for operations which are part of the WorldProcessor (running within its unit of parallelism) but specifically
 * related to information which undergoes a propagation strategy instead of distinct mutations (lighting, for example).
 * These are pulled out simply because these operations are completely distinct from normal world mutations and should
 * be considered/designed/optimized as their own concern.
 * 
 * TODO:  See if we benefit from parallelizing these (in a second "handleNextWorkUnit()" for the cuboid or making this
 * another top-level consideration, as a peer to WorldProcessor) by adding more complex block change merging to the
 * synchronization point (since each block would need to be updated in 2 ways).
 * TODO:  Consider calling using some pre-merging of WorldProcessor results such that we can run this within the same
 * tick as the mutations it is based on, in order to avoid the extra tick delay (note that this would require a sort of
 * delayed fence-style synchronization with WorldProcessor result merging within the tick - would add complexity to the
 * parallel logic loop design).
 */
public class PropagationHelpers
{
	/**
	 * Runs lighting update propagation on the data in the targetAddress cuboid by looking at changes from the previous
	 * tick.
	 * 
	 * @param targetAddress The address of the cuboid to update.
	 * @param potentialLightChangesByCuboid Per-cuboid block locations where lighting may have been changed by the
	 * previous tick.
	 * @param lazyLocalCache Used to resolve the mutable blocks within this cuboid (these may have been changed in this
	 * tick).
	 * @param lazyGlobalCache Used to resolve the read-only blocks within the loaded world (could be null if not
	 * loaded, but will otherwise show values from the previous tick).
	 */
	public static void processPreviousTickLightUpdates(CuboidAddress targetAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Function<AbsoluteLocation, MutableBlockProxy> lazyLocalCache
			, Function<AbsoluteLocation, BlockProxy> lazyGlobalCache
	)
	{
		// This requires that we check all possible lighting updates in this cuboid and surrounding ones and
		// reflood the lighting, writing back any changes in this cuboid.
		List<LightBringer.Light> lightsToAdd = new ArrayList<>();
		List<LightBringer.Light> lightsToRemove = new ArrayList<>();
		Map<AbsoluteLocation, Byte> lightValueOverlay = new HashMap<>();
		for (int x = -1; x <= 1; ++x)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int z = -1; z <= 1; ++z)
				{
					lightValueOverlay.putAll(_getAndSplitLightUpdates(lightsToAdd, lightsToRemove, potentialLightChangesByCuboid, lazyGlobalCache, targetAddress, x, y, z));
				}
			}
		}
		if (!lightsToAdd.isEmpty() || !lightsToRemove.isEmpty())
		{
			Environment env = Environment.getShared();
			LightBringer.IByteLookup lightLookup = (AbsoluteLocation location) ->
			{
				Byte overlayValue = lightValueOverlay.get(location);
				byte value;
				if (null == overlayValue)
				{
					BlockProxy proxy = lazyGlobalCache.apply(location);
					value = (null != proxy)
							? proxy.getLight()
							: LightBringer.IByteLookup.NOT_FOUND
					;
				}
				else
				{
					value = overlayValue;
				}
				return value;
			};
			LightBringer.IByteLookup opacityLookup = (AbsoluteLocation location) ->
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? env.lighting.getOpacity(proxy.getBlock())
						: LightBringer.IByteLookup.NOT_FOUND
				;
			};
			LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? env.lighting.getLightEmission(proxy.getBlock())
						: LightBringer.IByteLookup.NOT_FOUND
				;
			};
			Map<AbsoluteLocation, Byte> lightChanges = LightBringer.batchProcessLight(lightLookup
					, opacityLookup
					, sourceLookup
					, lightsToAdd
					, lightsToRemove
			);
			// First, set the lights based on our change starts.
			_flushLightChanges(targetAddress, lazyLocalCache, lightValueOverlay);
			// Now, re-update this with whatever was propagated.
			_flushLightChanges(targetAddress, lazyLocalCache, lightChanges);
		}
	}

	public static void processPreviousTickLogicUpdates(Consumer<IMutationBlock> updateMutations
			, CuboidAddress targetAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
			, Function<AbsoluteLocation, MutableBlockProxy> lazyLocalCache
			, Function<AbsoluteLocation, BlockProxy> lazyGlobalCache
	)
	{
		// In the future, we will need to worry about actual propagation but for now we only have direct sources and sinks.
		List<AbsoluteLocation> logicChanges = potentialLogicChangesByCuboid.get(targetAddress);
		if (null != logicChanges)
		{
			Environment env = Environment.getShared();
			for (AbsoluteLocation location : logicChanges)
			{
				// See if this block type disagrees with the logic value in the logic aspect.
				MutableBlockProxy mutable = lazyLocalCache.apply(location);
				byte logicLevel = mutable.getLogic();
				Block block = mutable.getBlock();
				byte expectedLogicLevel = (env.logic.isSource(block) && env.logic.isHigh(block))
						? LogicAspect.MAX_LEVEL
						: 0
				;
				
				if (logicLevel != expectedLogicLevel)
				{
					// Update the logic level.
					mutable.setLogic(expectedLogicLevel);
					
					// Send updates to the surrounding blocks so they can adapt to this change.
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 0, -1)));
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 0, 1)));
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, -1, 0)));
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 1, 0)));
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(-1, 0, 0)));
					updateMutations.accept(new MutationBlockLogicChange(location.getRelative(1, 0, 0)));
				}
			}
		}
	}


	private static Map<AbsoluteLocation, Byte> _getAndSplitLightUpdates(List<LightBringer.Light> lightsToAdd
			, List<LightBringer.Light> lightsToRemove
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Function<AbsoluteLocation, BlockProxy> proxyLookup
			, CuboidAddress targetAddress
			, int xOffset
			, int yOffset
			, int zOffset
	)
	{
		Map<AbsoluteLocation, Byte> changes = new HashMap<>();
		List<AbsoluteLocation> cuboidLights = potentialLightChangesByCuboid.get(targetAddress.getRelative(xOffset, yOffset, zOffset));
		if (null != cuboidLights)
		{
			for (AbsoluteLocation location : cuboidLights)
			{
				int distanceToCuboid = _distanceToCuboid(targetAddress, location);
				if (distanceToCuboid < LightAspect.MAX_LIGHT)
				{
					_processLightChange(lightsToAdd, lightsToRemove, changes, proxyLookup, location);
				}
			}
		}
		return changes;
	}

	private static void _processLightChange(List<LightBringer.Light> lightsToAdd
			, List<LightBringer.Light> lightsToRemove
			, Map<AbsoluteLocation, Byte> changes
			, Function<AbsoluteLocation, BlockProxy> proxyLookup
			, AbsoluteLocation location
	)
	{
		Environment env = Environment.getShared();
		// Read the block proxy to see if this if this something which is inconsistent with its emission or surrounding blocks and opacity.
		BlockProxy proxy = proxyLookup.apply(location);
		Block block = proxy.getBlock();
		byte currentLight = proxy.getLight();
		// Check if this is a light source.
		byte emission = env.lighting.getLightEmission(block);
		if (emission > currentLight)
		{
			// We need to add this light source.
			lightsToAdd.add(new LightBringer.Light(location, emission));
			changes.put(location, emission);
		}
		else
		{
			// See if this was an opaque block placed on top of something with light.
			byte opacity = env.lighting.getOpacity(block);
			if ((LightAspect.MAX_LIGHT == opacity) && (currentLight > 0))
			{
				// We need to set this to zero and cast the shadow.
				lightsToRemove.add(new LightBringer.Light(location, currentLight));
				changes.put(location, (byte)0);
			}
			else
			{
				// These are the more complex cases where the value changed for non-obvious reasons:
				// -block changed to different opacity (air <-> water, for example)
				// -opaque block was broken and needs computed light value
				
				// Check the surrounding blocks to see if the light should change.
				byte east = _lightLevelOrZero(proxyLookup.apply(location.getRelative( 1, 0, 0)));
				byte west = _lightLevelOrZero(proxyLookup.apply(location.getRelative(-1, 0, 0)));
				byte north = _lightLevelOrZero(proxyLookup.apply(location.getRelative(0, 1, 0)));
				byte south = _lightLevelOrZero(proxyLookup.apply(location.getRelative(0,-1, 0)));
				byte up = _lightLevelOrZero(proxyLookup.apply(location.getRelative(0, 0, 1)));
				byte down = _lightLevelOrZero(proxyLookup.apply(location.getRelative(0, 0,-1)));
				byte maxIncoming = _maxLight(east, west, north, south, up, down);
				byte calculated = (byte)(maxIncoming - opacity);
				if (calculated > currentLight)
				{
					// We add the new light level and reflow from here.
					lightsToAdd.add(new LightBringer.Light(location, calculated));
					changes.put(location, calculated);
				}
				else if (calculated < currentLight)
				{
					// We add the previous light level so we will quickly reflow from the actual source.
					lightsToRemove.add(new LightBringer.Light(location, currentLight));
					changes.put(location, (byte)0);
				}
				else
				{
					// This happens if the change didn't result in lighting change so do nothing.
				}
			}
		}
	}

	private static int _distanceToCuboid(CuboidAddress targetAddress, AbsoluteLocation location)
	{
		AbsoluteLocation base = targetAddress.getBase();
		AbsoluteLocation end = base.getRelative(32, 32, 32);
		
		// We check each dimension and sum them - this is "3D Manhattan distance".
		int distanceX = _distanceInDirection(base.x(), location.x(), end.x());
		int distanceY = _distanceInDirection(base.y(), location.y(), end.y());
		int distanceZ = _distanceInDirection(base.z(), location.z(), end.z());
		return distanceX + distanceY + distanceZ;
	}

	private static int _distanceInDirection(int base, int check, int end)
	{
		return (check < base)
				? (base - check)
				: (check > end)
					? (check - end)
					: 0
		;
	}

	private static byte _lightLevelOrZero(BlockProxy proxy)
	{
		return (null != proxy)
				? proxy.getLight()
				: 0
		;
	}

	private static byte _maxLight(byte east, byte west, byte north, byte south, byte up, byte down)
	{
		byte xMax = (byte)Math.max(east, west);
		byte yMax = (byte)Math.max(north, south);
		byte zMax = (byte)Math.max(up, down);
		byte max = (byte) Math.max(xMax, Math.max(yMax, zMax));
		return max;
	}

	private static void _flushLightChanges(CuboidAddress key
			, Function<AbsoluteLocation, MutableBlockProxy> lazyLocalCache
			, Map<AbsoluteLocation, Byte> lightChanges
	)
	{
		for (Map.Entry<AbsoluteLocation, Byte> change : lightChanges.entrySet())
		{
			AbsoluteLocation location = change.getKey();
			if (key.equals(location.getCuboidAddress()))
			{
				byte value = change.getValue();
				MutableBlockProxy proxy = lazyLocalCache.apply(location);
				proxy.setLight(value);
			}
		}
	}
}
