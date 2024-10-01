package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.jeffdisher.october.types.IByteLookup;


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
		// This for actual lighting updates so we map the interface directly to normal lighting values.
		Environment env = Environment.getShared();
		_ILightAccess accessor = new _ILightAccess() {
			@Override
			public byte getMaxLight()
			{
				return LightAspect.MAX_LIGHT;
			}
			@Override
			public byte getLightForLocation(AbsoluteLocation location)
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? proxy.getLight()
						: IByteLookup.NOT_FOUND
				;
			}
			@Override
			public byte getLightOrZero(AbsoluteLocation location)
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? proxy.getLight()
						: 0
				;
			}
			@Override
			public boolean setLightForLocation(AbsoluteLocation location, byte lightValue)
			{
				MutableBlockProxy proxy = lazyLocalCache.apply(location);
				boolean didChange = false;
				byte previous = proxy.getLight();
				if (previous != lightValue)
				{
					proxy.setLight(lightValue);
					didChange = true;
				}
				return didChange;
			}
			@Override
			public byte getEmissionForBlock(Block block)
			{
				return env.lighting.getLightEmission(block);
			}
			@Override
			public byte getOpacityForBlock(Block block)
			{
				return env.lighting.getOpacity(block);
			}
		};
		_runCommonFlood(env, accessor, targetAddress, potentialLightChangesByCuboid, lazyLocalCache, lazyGlobalCache);
	}

	public static void processPreviousTickLogicUpdates(Consumer<IMutationBlock> updateMutations
			, CuboidAddress targetAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
			, Function<AbsoluteLocation, MutableBlockProxy> lazyLocalCache
			, Function<AbsoluteLocation, BlockProxy> lazyGlobalCache
	)
	{
		// The logic works like lighting so we will use the generic facility by interpreting light as logic.
		Environment env = Environment.getShared();
		_ILightAccess accessor = new _ILightAccess() {
			@Override
			public byte getMaxLight()
			{
				return LogicAspect.MAX_LEVEL;
			}
			@Override
			public byte getLightForLocation(AbsoluteLocation location)
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? proxy.getLogic()
						: IByteLookup.NOT_FOUND
				;
			}
			@Override
			public byte getLightOrZero(AbsoluteLocation location)
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? proxy.getLogic()
						: 0
				;
			}
			@Override
			public boolean setLightForLocation(AbsoluteLocation location, byte lightValue)
			{
				MutableBlockProxy proxy = lazyLocalCache.apply(location);
				boolean didChange = false;
				byte previous = proxy.getLogic();
				if (previous != lightValue)
				{
					proxy.setLogic(lightValue);
					didChange = true;
				}
				return didChange;
			}
			@Override
			public byte getEmissionForBlock(Block block)
			{
				// We only ever emit max or nothing.
				return (env.logic.isSource(block) && env.logic.isHigh(block))
						? LogicAspect.MAX_LEVEL
						: 0
				;
			}
			@Override
			public byte getOpacityForBlock(Block block)
			{
				// Opacity is 1 for conduit blocks but 15 for everything else (even sources and sinks).
				return env.logic.isConduit(block)
						? 1
						: LogicAspect.MAX_LEVEL
				;
			}
		};
		Set<AbsoluteLocation> changeLocations = _runCommonFlood(env, accessor, targetAddress, potentialLogicChangesByCuboid, lazyLocalCache, lazyGlobalCache);
		for (AbsoluteLocation location : changeLocations)
		{
			// Send updates to the surrounding blocks so they can adapt to this change.
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 0, -1)));
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 0, 1)));
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, -1, 0)));
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(0, 1, 0)));
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(-1, 0, 0)));
			updateMutations.accept(new MutationBlockLogicChange(location.getRelative(1, 0, 0)));
		}
	}

	/**
	 * Used to determine the fraction of sky light to apply, given the time of day (in the range [0.0 .. 1.0]).
	 * This is put here since other light-related helpers are here.
	 * 
	 * @param gameTick The current game tick.
	 * @param ticksPerDay How many ticks exist within one full "day".
	 * @param dayStartOffset The point in ticksPerDay interval where the day "starts".
	 * @return The fraction of sky light for this time of day.
	 */
	public static float skyLightMultiplier(long gameTick, long ticksPerDay, long dayStartOffset)
	{
		return _skyLightMultiplier(gameTick, ticksPerDay, dayStartOffset);
	}

	/**
	 * Finds the physical light value to apply to sky-exposed blocks given the time of day (in the range [0..15]).
	 * This is put here since other light-related helpers are here.
	 * 
	 * @param gameTick The current game tick.
	 * @param ticksPerDay How many ticks exist within one full "day".
	 * @param dayStartOffset The point in ticksPerDay interval where the day "starts".
	 * @return The light value to apply to sky-exposed blocks.
	 */
	public static byte currentSkyLightValue(long gameTick, long ticksPerDay, long dayStartOffset)
	{
		float multiplier = _skyLightMultiplier(gameTick, ticksPerDay, dayStartOffset);
		return (byte)((float)LightAspect.MAX_LIGHT * multiplier);
	}

	/**
	 * A helper to calculate the new dayStartTick after running for some time.  The point of this is to return a number
	 * which will represent the same time of day after a server restart.
	 * 
	 * @param ticksRun The ticks run in this session.
	 * @param ticksPerDay The number of ticks per day.
	 * @param previousDayStartOffset The previous tick start offset.
	 * @return The new day start offset.
	 */
	public static long resumableStartTick(long ticksRun, long ticksPerDay, long previousDayStartOffset)
	{
		return (ticksRun + previousDayStartOffset) % ticksPerDay;
	}


	private static Set<AbsoluteLocation> _runCommonFlood(Environment env
			, _ILightAccess accessor
			, CuboidAddress targetAddress
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
					lightValueOverlay.putAll(_getAndSplitLightUpdates(accessor, lightsToAdd, lightsToRemove, potentialLightChangesByCuboid, lazyGlobalCache, targetAddress, x, y, z));
				}
			}
		}
		
		// We will also return the set of locations where changes occurred.
		Set<AbsoluteLocation> changedLocations = new HashSet<>();
		if (!lightsToAdd.isEmpty() || !lightsToRemove.isEmpty())
		{
			IByteLookup<AbsoluteLocation> lightLookup = (AbsoluteLocation location) ->
			{
				Byte overlayValue = lightValueOverlay.get(location);
				byte value;
				if (null == overlayValue)
				{
					value = accessor.getLightForLocation(location);
				}
				else
				{
					value = overlayValue;
				}
				return value;
			};
			IByteLookup<AbsoluteLocation> opacityLookup = (AbsoluteLocation location) ->
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? accessor.getOpacityForBlock(proxy.getBlock())
						: IByteLookup.NOT_FOUND
				;
			};
			IByteLookup<AbsoluteLocation> sourceLookup = (AbsoluteLocation location) ->
			{
				BlockProxy proxy = lazyGlobalCache.apply(location);
				return (null != proxy)
						? accessor.getEmissionForBlock(proxy.getBlock())
						: IByteLookup.NOT_FOUND
				;
			};
			Map<AbsoluteLocation, Byte> lightChanges = LightBringer.batchProcessLight(lightLookup
					, opacityLookup
					, sourceLookup
					, lightsToAdd
					, lightsToRemove
			);
			// First, set the lights based on our change starts.
			_flushLightChanges(accessor, targetAddress, lazyLocalCache, lightValueOverlay, changedLocations);
			// Now, re-update this with whatever was propagated.
			_flushLightChanges(accessor, targetAddress, lazyLocalCache, lightChanges, changedLocations);
		}
		return changedLocations;
	}

	private static Map<AbsoluteLocation, Byte> _getAndSplitLightUpdates(_ILightAccess accessor
			, List<LightBringer.Light> lightsToAdd
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
				if (distanceToCuboid < accessor.getMaxLight())
				{
					_processLightChange(accessor, lightsToAdd, lightsToRemove, changes, proxyLookup, location);
				}
			}
		}
		return changes;
	}

	private static void _processLightChange(_ILightAccess accessor
			, List<LightBringer.Light> lightsToAdd
			, List<LightBringer.Light> lightsToRemove
			, Map<AbsoluteLocation, Byte> changes
			, Function<AbsoluteLocation, BlockProxy> proxyLookup
			, AbsoluteLocation location
	)
	{
		// Read the block proxy to see if this if this something which is inconsistent with its emission or surrounding blocks and opacity.
		BlockProxy proxy = proxyLookup.apply(location);
		Block block = proxy.getBlock();
		byte currentLight = accessor.getLightForLocation(location);
		// Check if this is a light source.
		byte emission = accessor.getEmissionForBlock(block);
		if (emission > currentLight)
		{
			// We need to add this light source.
			lightsToAdd.add(new LightBringer.Light(location, emission));
			changes.put(location, emission);
		}
		else
		{
			// See if this was an opaque block placed on top of something with light.
			byte opacity = accessor.getOpacityForBlock(block);
			if ((accessor.getMaxLight() == opacity) && (currentLight > 0))
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
				byte east = accessor.getLightOrZero(location.getRelative( 1, 0, 0));
				byte west = accessor.getLightOrZero(location.getRelative(-1, 0, 0));
				byte north = accessor.getLightOrZero(location.getRelative(0, 1, 0));
				byte south = accessor.getLightOrZero(location.getRelative(0,-1, 0));
				byte up = accessor.getLightOrZero(location.getRelative(0, 0, 1));
				byte down = accessor.getLightOrZero(location.getRelative(0, 0,-1));
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

	private static byte _maxLight(byte east, byte west, byte north, byte south, byte up, byte down)
	{
		byte xMax = (byte)Math.max(east, west);
		byte yMax = (byte)Math.max(north, south);
		byte zMax = (byte)Math.max(up, down);
		byte max = (byte) Math.max(xMax, Math.max(yMax, zMax));
		return max;
	}

	private static void _flushLightChanges(_ILightAccess accessor
			, CuboidAddress key
			, Function<AbsoluteLocation, MutableBlockProxy> lazyLocalCache
			, Map<AbsoluteLocation, Byte> lightChanges
			, Set<AbsoluteLocation> inout_changedLocations
	)
	{
		for (Map.Entry<AbsoluteLocation, Byte> change : lightChanges.entrySet())
		{
			AbsoluteLocation location = change.getKey();
			if (key.equals(location.getCuboidAddress()))
			{
				byte value = change.getValue();
				boolean didChange = accessor.setLightForLocation(location, value);
				if (didChange)
				{
					inout_changedLocations.add(location);
				}
			}
		}
	}

	private static float _skyLightMultiplier(long gameTick, long ticksPerDay, long dayStartOffset)
	{
		// The dayStartOffset is usually 0, but is set to the offset into ticksPerDay where we should reposition the start of the day.
		long step = (gameTick + dayStartOffset) % ticksPerDay;
		// We actually need the light strength to cycle back and forth, not loop, so make this an abs function over half the day length.
		long ticksPerHalfDay = ticksPerDay / 2;
		return (float)Math.abs(step - ticksPerHalfDay) / (float)ticksPerHalfDay;
	}


	private static interface _ILightAccess
	{
		byte getMaxLight();
		byte getLightForLocation(AbsoluteLocation location);
		byte getLightOrZero(AbsoluteLocation location);
		boolean setLightForLocation(AbsoluteLocation location, byte lightValue);
		byte getEmissionForBlock(Block block);
		byte getOpacityForBlock(Block block);
	}
}
