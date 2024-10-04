package com.jeffdisher.october.logic;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.LazyLocationCache;
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
	 * @param cuboidHeightMaps The map of read-only per-cuboid height maps.
	 * @param context The context used for running changes.
	 * @param mutationsToRun The map of mutations to run in this tick, keyed by cuboid addresses where they are
	 * scheduled.
	 * @param modifiedBlocksByCuboidAddress The map of which blocks where updated in the previous tick.
	 * @param potentialLightChangesByCuboid The map of block locations which may have incurred lighting updates in the
	 * previous tick.
	 * @param potentialLogicChangesByCuboid The map of block locations which may have incurred logic updates in the
	 * previous tick.
	 * @param cuboidsLoadedThisTick The set of cuboids which were loaded this tick (for update even synthesis).
	 * @return The subset of the mutationsToRun work which was completed by this thread.
	 */
	public static ProcessedFragment processWorldFragmentParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps
			, TickProcessingContext context
			, Map<CuboidAddress, List<ScheduledMutation>> mutationsToRun
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		long millisSinceLastTick = context.millisPerTick;
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		Map<CuboidAddress, CuboidHeightMap> fragmentHeights = new HashMap<>();
		
		// We need to walk all the loaded cuboids, just to make sure that there were no updates.
		Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid = new HashMap<>();
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		int committedMutationCount = 0;
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				processor.cuboidsProcessed += 1;
				CuboidAddress key = elt.getKey();
				IReadOnlyCuboidData oldState = elt.getValue();
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != oldState);
				// We will accumulate changing blocks and determine if we need to write any back at the end.
				LazyLocationCache<MutableBlockProxy> lazyMutableBlockCache = new LazyLocationCache<>(
						(AbsoluteLocation location) ->  new MutableBlockProxy(location, oldState)
				);
				
				// We want to synthesize block updates adjacent to modified blocks and, optionally, boundaries of fresh cuboids.
				// NOTE:  This is disabled by default since it washes out all performance values and will be replaced with something more precise in the future.
				committedMutationCount += _synthesizeAndRunBlockUpdates(processor
						, context.config.shouldSynthesizeUpdatesOnLoad
						, lazyMutableBlockCache
						, context
						, oldState
						, modifiedBlocksByCuboidAddress
						, worldMap.keySet()
						, cuboidsLoadedThisTick
				);
				
				// Now run the normal mutations.
				List<ScheduledMutation> mutations = mutationsToRun.get(key);
				Map<AbsoluteLocation, Long> periodicNotReadyByLocation = new HashMap<>();
				if (null != mutations)
				{
					for (ScheduledMutation scheduledMutations : mutations)
					{
						long millisUntilReady = scheduledMutations.millisUntilReady();
						IMutationBlock mutation = scheduledMutations.mutation();
						if (0L == millisUntilReady)
						{
							processor.cuboidMutationsProcessed += 1;
							boolean didApply = _runOneMutation(lazyMutableBlockCache, context, oldState, mutation);
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
							periodicNotReadyByLocation.put(mutation.getAbsoluteLocation(), updatedMillis);
						}
					}
				}
				
				// We also want to process lighting updates from the previous tick.
				PropagationHelpers.processPreviousTickLightUpdates(key, potentialLightChangesByCuboid, lazyMutableBlockCache, context.previousBlockLookUp);
				
				// While here, also process any logic aspect updates from the previous tick.
				PropagationHelpers.processPreviousTickLogicUpdates((IMutationBlock update) -> context.mutationSink.next(update)
						, key
						, potentialLogicChangesByCuboid
						, lazyMutableBlockCache
						, context.previousBlockLookUp
				);
				
				// Return the old instance if nothing changed.
				List<MutableBlockProxy> proxiesToWrite = lazyMutableBlockCache.getCachedValues().stream().filter(
						(MutableBlockProxy proxy) -> proxy.didChange()
				).toList();
				if (!proxiesToWrite.isEmpty())
				{
					// Something changed so we will write-back the updated cuboid and list of block state changes.
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
					
					// For now, we will regenerate the maps in this case but we may want to update more precisely.
					fragmentHeights.put(key, HeightMapHelpers.buildHeightMap(mutable));
					
					// Add the change descriptions for this cuboid.
					blockChangesByCuboid.put(key, updateMutations);
				}
				
				// Write back any of the updated periodic events.
				List<MutableBlockProxy> proxiesWithScheduledMutations = lazyMutableBlockCache.getCachedValues().stream().filter(
						(MutableBlockProxy proxy) -> (proxy.periodicDelayMillis > 0L)
				).toList();
				for (MutableBlockProxy proxy : proxiesWithScheduledMutations)
				{
					AbsoluteLocation location = proxy.absoluteLocation;
					long existing = periodicNotReadyByLocation.containsKey(location)
							? periodicNotReadyByLocation.get(location)
							: Long.MAX_VALUE
					;
					long updated = Math.min(proxy.periodicDelayMillis, existing);
					periodicNotReadyByLocation.put(location, updated);
				}
				for (Map.Entry<AbsoluteLocation, Long> periodic : periodicNotReadyByLocation.entrySet())
				{
					MutationBlockPeriodic mutation = new MutationBlockPeriodic(periodic.getKey());
					notYetReadyMutations.add(new ScheduledMutation(mutation, periodic.getValue()));
				}
			}
		}
		
		// We package up any of the work that we did (note that no thread will return a cuboid which had no mutations in its fragment).
		return new ProcessedFragment(fragment
				, fragmentHeights
				, notYetReadyMutations
				, blockChangesByCuboid
				, committedMutationCount
		);
	}

	private static boolean _runOneMutation(Function<AbsoluteLocation, MutableBlockProxy> lazyMutableBlockCache
			, TickProcessingContext context
			, IReadOnlyCuboidData oldState
			, IMutationBlock mutation
	)
	{
		AbsoluteLocation absoluteLocation = mutation.getAbsoluteLocation();
		MutableBlockProxy thisBlockProxy = lazyMutableBlockCache.apply(absoluteLocation);
		return mutation.applyMutation(context, thisBlockProxy);
	}

	private static int _synthesizeAndRunBlockUpdates(ProcessorElement processor
			, boolean shouldIncludeLoadedCuboidFaces
			, Function<AbsoluteLocation, MutableBlockProxy> lazyMutableBlockCache
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
		
		// We always synthesize update events for modified blocks.
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 0, 0, 0);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 0, 0, -1);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 0, 0, 1);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 0, -1, 0);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 0, 1, 0);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, -1, 0, 0);
		_collectBlocksAdjacentToChanges(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, 1, 0, 0);
		
		// Optionally, synthesize update events for all loaded cuboid faces (and opposing faces)
		if (shouldIncludeLoadedCuboidFaces)
		{
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, 0);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, -1);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 0, 1);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, -1, 0);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 0, 1, 0);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, -1, 0, 0);
			_collectFacesOfNewCuboids(toSynthesize, thisAddress, allLoadedCuboids, cuboidsLoadedThisTick, 1, 0, 0);
		}
		
		// Now, walk that set, synthesize and run a block update on each.
		int appliedUpdates = 0;
		for (AbsoluteLocation target : toSynthesize)
		{
			MutationBlockUpdate update = new MutationBlockUpdate(target);
			processor.cuboidBlockupdatesProcessed += 1;
			boolean didApply = _runOneMutation(lazyMutableBlockCache, context, oldState, update);
			if (didApply)
			{
				appliedUpdates += 1;
			}
		}
		return appliedUpdates;
	}

	private static void _collectFacesOfNewCuboids(Set<AbsoluteLocation> inout_toSynthesize
			, CuboidAddress targetCuboid
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
	}

	private static void _collectBlocksAdjacentToChanges(Set<AbsoluteLocation> inout_toSynthesize
			, CuboidAddress targetCuboid
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, int relX
			, int relY
			, int relZ
	)
	{
		CuboidAddress checkingCuboid = targetCuboid.getRelative(relX, relY, relZ);
		if (modifiedBlocksByCuboidAddress.containsKey(checkingCuboid))
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


	public static record ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, Map<CuboidAddress, CuboidHeightMap> heightFragment
			, List<ScheduledMutation> notYetReadyMutations
			, Map<CuboidAddress, List<BlockChangeDescription>> blockChangesByCuboid
			, int committedMutationCount
	) {}
}
