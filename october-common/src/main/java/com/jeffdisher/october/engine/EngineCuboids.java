package com.jeffdisher.october.engine;

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
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.ScheduledMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.LazyLocationCache;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static engine logic related to processing cuboids in the world.
 */
public class EngineCuboids
{
	private EngineCuboids()
	{
		// This is just static logic.
	}

	/**
	 * Applies the given mutationsToRun to the given oldState, returning the results.
	 * 
	 * @param context The context used for running changes.
	 * @param allLoadedCuboids The set of all loaded cuboid addresses loaded in the current tick.
	 * @param mutationsToRun The list of mutations to run against this cuboid in the current tick or future.
	 * @param periodicToRun The map of blocks in this cuboid to when they next need periodic events run (in millis).
	 * @param modifiedBlocksByCuboidAddress The map of which blocks where updated in the previous tick.
	 * @param potentialLightChangesByCuboid The map of block locations which may have incurred lighting updates in the
	 * previous tick.
	 * @param potentialLogicChangesByCuboid The map of block locations which may have incurred logic updates in the
	 * previous tick.
	 * @param cuboidsLoadedThisTick The set of cuboids which were loaded this tick (for update even synthesis).
	 * @param key The address of the cuboid being processed.
	 * @param oldState The read-only input state of the cuboid to be processed.
	 * @return The results of running these mutations and periodic events on this cuboid.
	 */
	public static SingleCuboidResult processOneCuboid(TickProcessingContext context
		, Set<CuboidAddress> allLoadedCuboids
		, List<ScheduledMutation> mutationsToRun
		, Map<BlockAddress, Long> periodicToRun
		, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
		, Map<CuboidAddress, List<AbsoluteLocation>> potentialLightChangesByCuboid
		, Map<CuboidAddress, List<AbsoluteLocation>> potentialLogicChangesByCuboid
		, Set<CuboidAddress> cuboidsLoadedThisTick
		, CuboidAddress key
		, IReadOnlyCuboidData oldState
	)
	{
		long millisSinceLastTick = context.millisPerTick;
		// We can't be told to operate on something which isn't in the state.
		Assert.assertTrue(null != oldState);
		// We will accumulate changing blocks and determine if we need to write any back at the end.
		LazyLocationCache<MutableBlockProxy> lazyMutableBlockCache = new LazyLocationCache<>(
				(AbsoluteLocation location) ->  new MutableBlockProxy(location, oldState)
		);
		
		// We want to synthesize block updates adjacent to modified blocks and, optionally, boundaries of fresh cuboids.
		// NOTE:  This is disabled by default since it washes out all performance values and will be replaced with something more precise in the future.
		_UpdateResults updateResults = _synthesizeAndRunBlockUpdates(context
			, context.config.shouldSynthesizeUpdatesOnLoad
			, lazyMutableBlockCache
			, oldState
			, modifiedBlocksByCuboidAddress
			, allLoadedCuboids
			, cuboidsLoadedThisTick
		);
		int blockUpdatesProcessed = updateResults.blockUpdatesProcessed;
		int blockUpdatesApplied = updateResults.blockUpdatesApplied;
		int mutationsProcessed = 0;
		int mutationsApplied = 0;
		
		// Run any periodic mutations which have been requested.
		Map<BlockAddress, Long> periodicNotReady = new HashMap<>();
		if (null != periodicToRun)
		{
			for (Map.Entry<BlockAddress, Long> ent : periodicToRun.entrySet())
			{
				BlockAddress block = ent.getKey();
				long millisUntilReady = ent.getValue();
				if (0L == millisUntilReady)
				{
					// Synthesize this.
					MutationBlockPeriodic mutation = new MutationBlockPeriodic(key.getBase().relativeForBlock(block));
					mutationsProcessed += 1;
					boolean didApply = _runOneMutation(lazyMutableBlockCache, context, oldState, mutation);
					if (didApply)
					{
						mutationsApplied += 1;
					}
				}
				else
				{
					long updatedMillis = millisUntilReady - millisSinceLastTick;
					if (updatedMillis < 0L)
					{
						updatedMillis = 0L;
					}
					periodicNotReady.put(block, updatedMillis);
				}
			}
		}
		
		// Now run the normal mutations.
		List<ScheduledMutation> notYetReadyMutations = new ArrayList<>();
		if (null != mutationsToRun)
		{
			for (ScheduledMutation scheduledMutations : mutationsToRun)
			{
				long millisUntilReady = scheduledMutations.millisUntilReady();
				IMutationBlock mutation = scheduledMutations.mutation();
				if (0L == millisUntilReady)
				{
					mutationsProcessed += 1;
					boolean didApply = _runOneMutation(lazyMutableBlockCache, context, oldState, mutation);
					if (didApply)
					{
						mutationsApplied += 1;
					}
				}
				else
				{
					long updatedMillis = millisUntilReady - millisSinceLastTick;
					if (updatedMillis < 0L)
					{
						updatedMillis = 0L;
					}
					ScheduledMutation updated = new ScheduledMutation(mutation, updatedMillis);
					notYetReadyMutations.add(updated);
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
		IReadOnlyCuboidData changedCuboidOrNull = null;
		CuboidHeightMap changedHeightMap = null;
		List<BlockChangeDescription> changedBlocks = null;
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
			changedCuboidOrNull = mutable;
			
			// For now, we will regenerate the maps in this case but we may want to update more precisely.
			changedHeightMap = HeightMapHelpers.buildHeightMap(mutable);
			
			// Add the change descriptions for this cuboid.
			changedBlocks = updateMutations;
		}
		
		// Write back any of the updated periodic events.
		List<MutableBlockProxy> proxiesWithScheduledMutations = lazyMutableBlockCache.getCachedValues().stream().filter(
				(MutableBlockProxy proxy) -> (proxy.periodicDelayMillis > 0L)
		).toList();
		for (MutableBlockProxy proxy : proxiesWithScheduledMutations)
		{
			BlockAddress block = proxy.absoluteLocation.getBlockAddress();
			long existing = periodicNotReady.containsKey(block)
					? periodicNotReady.get(block)
					: Long.MAX_VALUE
			;
			long updated = Math.min(proxy.periodicDelayMillis, existing);
			periodicNotReady.put(block, updated);
		}
		return new SingleCuboidResult(changedCuboidOrNull
			, changedHeightMap
			, changedBlocks
			, periodicNotReady.isEmpty() ? null : periodicNotReady
			, notYetReadyMutations.isEmpty() ? null : notYetReadyMutations
			, blockUpdatesProcessed
			, blockUpdatesApplied
			, mutationsProcessed
			, mutationsApplied
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

	private static _UpdateResults _synthesizeAndRunBlockUpdates(TickProcessingContext context
			, boolean shouldIncludeLoadedCuboidFaces
			, Function<AbsoluteLocation, MutableBlockProxy> lazyMutableBlockCache
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
		int blockUpdatesProcessed = toSynthesize.size();
		int appliedUpdates = 0;
		for (AbsoluteLocation target : toSynthesize)
		{
			MutationBlockUpdate update = new MutationBlockUpdate(target);
			boolean didApply = _runOneMutation(lazyMutableBlockCache, context, oldState, update);
			if (didApply)
			{
				appliedUpdates += 1;
			}
		}
		return new _UpdateResults(blockUpdatesProcessed, appliedUpdates);
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


	public static record SingleCuboidResult(IReadOnlyCuboidData changedCuboidOrNull
		, CuboidHeightMap changedHeightMap
		, List<BlockChangeDescription> changedBlocks
		, Map<BlockAddress, Long> periodicNotReady
		, List<ScheduledMutation> notYetReadyMutations
		, int blockUpdatesProcessed
		, int blockUpdatesApplied
		, int mutationsProcessed
		, int mutationsApplied
	) {}

	private static record _UpdateResults(int blockUpdatesProcessed, int blockUpdatesApplied) {}
}
