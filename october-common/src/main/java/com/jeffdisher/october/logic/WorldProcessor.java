package com.jeffdisher.october.logic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static logic which implements the parallel game tick logic when operating on cuboids within the world.
 * The counterpart to this, for entities, is CrowdProcessor.
 */
public class WorldProcessor
{
	private WorldProcessor()
	{
		// This is just static logic.
	}

	/**
	 * Applies the given mutationsToRun to the data in worldMap, returning updated cuboids for some subset of the
	 * mutations.
	 * Note that this is expected to be run in parallel, across many threads, and will rely on a bakery algorithm to
	 * select each thread's subset of the work, dynamically.  The fragments returned by all threads will have no overlap
	 * and the union of all of them will entirely cover the key space defined by mutationsToRun.
	 * 
	 * @param processor The current thread.
	 * @param worldMap The map of all read-only cuboids from the previous tick.
	 * @param loader Used to resolve read-only block data from the previous tick.
	 * @param gameTick The game tick being processed.
	 * @param millisSinceLastTick Milliseconds based since last tick.
	 * @param mutationsToRun The map of mutations to run in this tick, keyed by cuboid addresses where they are
	 * scheduled.
	 * @param modifiedBlocksByCuboidAddress The map of which blocks where updated in the previous tick.
	 * @param potentialLightChangesByCuboid The map of block locations which may have incurred lighting updates in the
	 * previous tick.
	 * @param cuboidsLoadedThisTick The set of cuboids which were loaded this tick (for update even synthesis).
	 * @return The subset of the mutationsToRun work which was completed by this thread.
	 */
	public static ProcessedFragment processWorldFragmentParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, long millisSinceLastTick
			, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		
		// We need to walk all the loaded cuboids, just to make sure that there were no updates.
		Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
		List<ScheduledMutation> delayedMutations = new ArrayList<>();
		int committedMutationCount = 0;
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				CuboidAddress key = elt.getKey();
				IReadOnlyCuboidData oldState = elt.getValue();
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != oldState);
				// We will accumulate changing blocks and determine if we need to write any back at the end.
				Map<BlockAddress, MutableBlockProxy> proxies = new HashMap<>();
				
				BasicBlockProxyCache local = new BasicBlockProxyCache(loader);
				TickProcessingContext context = new TickProcessingContext(gameTick, local, newMutationSink, newChangeSink);
				
				// First, handle block updates.
				committedMutationCount += _synthesizeAndRunBlockUpdates(proxies
						, context
						, oldState
						, modifiedBlocksByCuboidAddress
						, worldMap.keySet()
						, cuboidsLoadedThisTick
				);
				
				// Now run the normal mutations.
				List<ScheduledMutation> mutations = mutationsToRun.get(key);
				if (null != mutations)
				{
					for (ScheduledMutation scheduledMutations : mutations)
					{
						long millisUntilReady = scheduledMutations.millisUntilReady();
						IMutationBlock mutation = scheduledMutations.mutation();
						if (0L == millisUntilReady)
						{
							processor.mutationCount += 1;
							boolean didApply = _runOneMutation(proxies, context, oldState, mutation);
							if (didApply)
							{
								committedMutationCount += 1;
							}
						}
						else
						{
							long updatedMillis = millisUntilReady - millisSinceLastTick;
							if (updatedMillis < 0L)
							{
								updatedMillis = 0L;
							}
							delayedMutations.add(new ScheduledMutation(mutation, updatedMillis));
						}
					}
				}
				
				// We also want to process lighting updates from the previous tick.
				_processPreviousTickLightUpdates(key, oldState, potentialLightChangesByCuboid, proxies, local);
				
				// Return the old instance if nothing changed.
				List<MutableBlockProxy> proxiesToWrite = proxies.values().stream().filter(
						(MutableBlockProxy proxy) -> proxy.didChange()
				).toList();
				if (proxiesToWrite.isEmpty())
				{
					// There were no actual changes to this cuboid so just use the old state.
					fragment.put(key, oldState);
				}
				else
				{
					ByteBuffer scratchBuffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES - PacketCodec.HEADER_BYTES);
					List<BlockChangeDescription> updateMutations = new ArrayList<>();
					// At least something changed so create a new clone and write-back into it.
					CuboidData mutable = CuboidData.mutableClone(oldState);
					for (MutableBlockProxy proxy : proxiesToWrite)
					{
						proxy.writeBack(mutable);
						
						// Since this one changed, we also want to send the set block mutation.
						updateMutations.add(BlockChangeDescription.extractFromProxy(scratchBuffer, proxy));
					}
					fragment.put(key, mutable);
					
					// Add the change descriptions for this cuboid.
					blockChangesByCuboid.put(key, updateMutations);
				}
			}
		}
		
		List<ScheduledMutation> exportedMutations = newMutationSink.takeExportedMutations();
		exportedMutations.addAll(delayedMutations);
		Map<Integer, List<ScheduledChange>> exportedEntityChanges = newChangeSink.takeExportedChanges();
		// We package up any of the work that we did (note that no thread will return a cuboid which had no mutations in its fragment).
		return new ProcessedFragment(fragment
				, exportedMutations
				, exportedEntityChanges
				, blockChangesByCuboid
				, committedMutationCount
		);
	}

	private static boolean _runOneMutation(Map<BlockAddress, MutableBlockProxy> inout_proxies
			, TickProcessingContext context
			, IReadOnlyCuboidData oldState
			, IMutationBlock mutation
	)
	{
		AbsoluteLocation absoluteLocation = mutation.getAbsoluteLocation();
		BlockAddress address = absoluteLocation.getBlockAddress();
		MutableBlockProxy thisBlockProxy = inout_proxies.get(address);
		if (null == thisBlockProxy)
		{
			thisBlockProxy = new MutableBlockProxy(absoluteLocation, oldState);
			inout_proxies.put(address, thisBlockProxy);
		}
		return mutation.applyMutation(context, thisBlockProxy);
	}

	private static int _synthesizeAndRunBlockUpdates(Map<BlockAddress, MutableBlockProxy> inout_proxies
			, TickProcessingContext context
			, IReadOnlyCuboidData oldState
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Set<CuboidAddress> allLoadedCuboids
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		// Look at the updates for the 7 cuboids we care about (this one and the 6 adjacent):
		// -check each of the 6 blocks around each updated location
		// -if any of those locations are in our current cuboid, add them to a set (avoids duplicates)
		CuboidAddress thisAddress = oldState.getCuboidAddress();
		Set<AbsoluteLocation> toSynthesize = new HashSet<>();
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, -1);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, 1);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, -1, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 1, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, -1, 0, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, allLoadedCuboids, cuboidsLoadedThisTick, 1, 0, 0);
		
		// Now, walk that set, synthesize and run a block update on each.
		int appliedUpdates = 0;
		for (AbsoluteLocation target : toSynthesize)
		{
			MutationBlockUpdate update = new MutationBlockUpdate(target);
			boolean didApply = _runOneMutation(inout_proxies, context, oldState, update);
			if (didApply)
			{
				appliedUpdates += 1;
			}
		}
		return appliedUpdates;
	}

	private static void _checkCuboid(Set<AbsoluteLocation> inout_toSynthesize
			, CuboidAddress targetCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Set<CuboidAddress> allLoadedCuboids
			, Set<CuboidAddress> cuboidsLoadedThisTick
			, int relX
			, int relY
			, int relZ
	)
	{
		CuboidAddress checkingCuboid = targetCuboid.getRelative(relX, relY, relZ);
		// Note that we will check this in 2 directions:
		// 1) We need to run an update on each face bordering a cuboid just loaded this tick.
		boolean isFacingNewCuboid = !targetCuboid.equals(checkingCuboid) && cuboidsLoadedThisTick.contains(checkingCuboid);
		// 2) If WE were just loaded, we need to run and update on each face bordering a loaded cuboid.
		boolean isNewFacingLoaded = cuboidsLoadedThisTick.contains(targetCuboid) && allLoadedCuboids.contains(checkingCuboid);
		if (isFacingNewCuboid || isNewFacingLoaded)
		{
			// We need to synthesize the entire face of targetCuboid which is touching checkingCuboid.
			AbsoluteLocation cuboidBase = targetCuboid.getBase();
			if (0 != relX)
			{
				int x = (1 == relX) ? 31 : 0;
				for (int y = 0; y < 32; ++y)
				{
					for (int z = 0; z < 32; ++z)
					{
						inout_toSynthesize.add(cuboidBase.getRelative(x, y, z));
					}
				}
			}
			if (0 != relY)
			{
				int y = (1 == relY) ? 31 : 0;
				for (int x = 0; x < 32; ++x)
				{
					for (int z = 0; z < 32; ++z)
					{
						inout_toSynthesize.add(cuboidBase.getRelative(x, y, z));
					}
				}
			}
			if (0 != relZ)
			{
				int z = (1 == relZ) ? 31 : 0;
				for (int x = 0; x < 32; ++x)
				{
					for (int y = 0; y < 32; ++y)
					{
						inout_toSynthesize.add(cuboidBase.getRelative(x, y, z));
					}
				}
			}
		}
		else if (modifiedBlocksByCuboidAddress.containsKey(checkingCuboid))
		{
			List<AbsoluteLocation> modifiedBlocks = modifiedBlocksByCuboidAddress.get(checkingCuboid);
			for (AbsoluteLocation modified : modifiedBlocks)
			{
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(0, 0, -1));
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(0, 0, 1));
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(0, -1, 0));
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(0, 1, 0));
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(-1, 0, 0));
				_checkLocation(inout_toSynthesize, targetCuboid, modified.getRelative(1, 0, 0));
			}
		}
	}

	private static void _checkLocation(Set<AbsoluteLocation> inout_toSynthesize
			, CuboidAddress targetCuboid
			, AbsoluteLocation location
	)
	{
		if (targetCuboid.equals(location.getCuboidAddress()))
		{
			inout_toSynthesize.add(location);
		}
	}

	private static void _processPreviousTickLightUpdates(CuboidAddress targetAddress
			, IReadOnlyCuboidData oldState
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Map<BlockAddress, MutableBlockProxy> proxies
			, Function<AbsoluteLocation, BlockProxy> local
	)
	{
		// This requires that we check all possible lighting updates in this cuboid and surrounding ones and
		// reflood the lighting, writing back any changes in this cuboid.
		// TODO:  See if we benefit from parallelizing this (in a second "handleNextWorkUnit()" for the cuboid)
		// by adding more complex block change merging to the synchronization point (since each block would need
		// to be updated in 2 ways).
		List<LightBringer.Light> lightsToAdd = new ArrayList<>();
		List<LightBringer.Light> lightsToRemove = new ArrayList<>();
		Map<AbsoluteLocation, Byte> lightValueOverlay = new HashMap<>();
		for (int x = -1; x <= 1; ++x)
		{
			for (int y = -1; y <= 1; ++y)
			{
				for (int z = -1; z <= 1; ++z)
				{
					lightValueOverlay.putAll(_getAndSplitLightUpdates(lightsToAdd, lightsToRemove, potentialLightChangesByCuboid, local, targetAddress, x, y, z));
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
					BlockProxy proxy = local.apply(location);
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
				BlockProxy proxy = local.apply(location);
				return (null != proxy)
						? env.lighting.getOpacity(proxy.getBlock())
						: LightBringer.IByteLookup.NOT_FOUND
				;
			};
			LightBringer.IByteLookup sourceLookup = (AbsoluteLocation location) ->
			{
				BlockProxy proxy = local.apply(location);
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
			_flushLightChanges(targetAddress, oldState, proxies, lightValueOverlay);
			// Now, re-update this with whatever was propagated.
			_flushLightChanges(targetAddress, oldState, proxies, lightChanges);
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
			, IReadOnlyCuboidData oldState
			, Map<BlockAddress, MutableBlockProxy> proxies
			, Map<AbsoluteLocation, Byte> lightChanges
	)
	{
		for (Map.Entry<AbsoluteLocation, Byte> change : lightChanges.entrySet())
		{
			AbsoluteLocation location = change.getKey();
			if (key.equals(location.getCuboidAddress()))
			{
				byte value = change.getValue();
				BlockAddress block = location.getBlockAddress();
				MutableBlockProxy proxy = proxies.get(block);
				if (null == proxy)
				{
					proxy = new MutableBlockProxy(location, oldState);
					proxies.put(block, proxy);
				}
				proxy.setLight(value);
			}
		}
	}


	public static record ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, List<ScheduledMutation> exportedMutations
			, Map<Integer, List<ScheduledChange>> exportedEntityChanges
			, Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid
			, int committedMutationCount
	) {}
}
