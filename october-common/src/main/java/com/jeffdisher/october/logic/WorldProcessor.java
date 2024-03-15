package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IBlockStateUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.types.AbsoluteLocation;
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
	 * @param mutationsToRun The map of mutations to run in this tick, keyed by cuboid addresses where they are
	 * scheduled.
	 * @param modifiedBlocksByCuboidAddress The map of which blocks where updated in the previous tick.
	 * @param cuboidsLoadedThisTick The set of cuboids which were loaded this tick (for update even synthesis).
	 * @return The subset of the mutationsToRun work which was completed by this thread.
	 */
	public static ProcessedFragment processWorldFragmentParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, Map<CuboidAddress, List<IMutationBlock>> mutationsToRun
			, Map<CuboidAddress, List<AbsoluteLocation>> modifiedBlocksByCuboidAddress
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		List<IMutationBlock> exportedMutations = new ArrayList<>();
		Map<Integer, List<IMutationEntity>> exportedEntityChanges = new HashMap<>();
		Consumer<IMutationBlock> sink = new Consumer<IMutationBlock>() {
			@Override
			public void accept(IMutationBlock arg0)
			{
				// Note that it may be worth pre-filtering the mutations to eagerly schedule them against this cuboid but that seems like needless complexity.
				exportedMutations.add(arg0);
			}};
			
			TickProcessingContext.IChangeSink newChangeSink = new TickProcessingContext.IChangeSink() {
			@Override
			public void accept(int targetEntityId, IMutationEntity change)
			{
				List<IMutationEntity> entityChanges = exportedEntityChanges.get(targetEntityId);
				if (null == entityChanges)
				{
					entityChanges = new LinkedList<>();
					exportedEntityChanges.put(targetEntityId, entityChanges);
				}
				entityChanges.add(change);
			}
		};
		TickProcessingContext context = new TickProcessingContext(gameTick, loader, sink, newChangeSink);
		
		// We need to walk all the loaded cuboids, just to make sure that there were no updates.
		Map<CuboidAddress, List<IBlockStateUpdate>> resultantMutationsByCuboid = new HashMap<>();
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
				
				// First, handle block updates.
				committedMutationCount += _synthesizeAndRunBlockUpdates(proxies, context, oldState, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick);
				
				// Now run the normal mutations.
				List<IMutationBlock> mutations = mutationsToRun.get(key);
				if (null != mutations)
				{
					for (IMutationBlock mutation : mutations)
					{
						processor.mutationCount += 1;
						boolean didApply = _runOneMutation(proxies, context, oldState, mutation);
						if (didApply)
						{
							committedMutationCount += 1;
						}
					}
				}
				
				// Return the old instance if nothing changed.
				List<MutableBlockProxy> proxiesToWrite = new ArrayList<>();
				List<IBlockStateUpdate> updateMutations = new ArrayList<>();
				for (MutableBlockProxy proxy : proxies.values())
				{
					if (proxy.didChange())
					{
						proxiesToWrite.add(proxy);
						// Since this one changed, we also want to send the set block mutation.
						updateMutations.add(MutationBlockSetBlock.extractFromProxy(proxy));
					}
				}
				if (proxiesToWrite.isEmpty())
				{
					// There were no actual changes to this cuboid so just use the old state.
					fragment.put(key, oldState);
					Assert.assertTrue(updateMutations.isEmpty());
				}
				else
				{
					// At least something changed so create a new clone and write-back into it.
					CuboidData mutable = CuboidData.mutableClone(oldState);
					for (MutableBlockProxy proxy : proxiesToWrite)
					{
						proxy.writeBack(mutable);
					}
					fragment.put(key, mutable);
					
					// Add the mutations associated with updating this cuboid.
					resultantMutationsByCuboid.put(key, updateMutations);
				}
			}
		}
		// We package up any of the work that we did (note that no thread will return a cuboid which had no mutations in its fragment).
		return new ProcessedFragment(fragment
				, exportedMutations
				, exportedEntityChanges
				, resultantMutationsByCuboid
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
			, Set<CuboidAddress> cuboidsLoadedThisTick
	)
	{
		// Look at the updates for the 7 cuboids we care about (this one and the 6 adjacent):
		// -check each of the 6 blocks around each updated location
		// -if any of those locations are in our current cuboid, add them to a set (avoids duplicates)
		CuboidAddress thisAddress = oldState.getCuboidAddress();
		Set<AbsoluteLocation> toSynthesize = new HashSet<>();
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 0, 0, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 0, 0, -1);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 0, 0, 1);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 0, -1, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 0, 1, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, -1, 0, 0);
		_checkCuboid(toSynthesize, thisAddress, modifiedBlocksByCuboidAddress, cuboidsLoadedThisTick, 1, 0, 0);
		
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
			, Set<CuboidAddress> cuboidsLoadedThisTick
			, int relX
			, int relY
			, int relZ
	)
	{
		CuboidAddress checkingCuboid = targetCuboid.getRelative(relX, relY, relZ);
		if (!targetCuboid.equals(checkingCuboid) && cuboidsLoadedThisTick.contains(checkingCuboid))
		{
			// We need to synthesize the entire face of targetCuboid which is touching checkingCuboid.
			if (0 != relX)
			{
				int x = (1 == relX) ? 31 : 0;
				for (int y = 0; y < 32; ++y)
				{
					for (int z = 0; z < 32; ++z)
					{
						inout_toSynthesize.add(new AbsoluteLocation(x, y, z));
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
						inout_toSynthesize.add(new AbsoluteLocation(x, y, z));
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
						inout_toSynthesize.add(new AbsoluteLocation(x, y, z));
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


	public static record ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, List<IMutationBlock> exportedMutations
			, Map<Integer, List<IMutationEntity>> exportedEntityChanges
			// Note that the resultantMutationsByCuboid may not be the input mutations, but will have an equivalent impact on the world.
			, Map<CuboidAddress, List<IBlockStateUpdate>> resultantMutationsByCuboid
			, int committedMutationCount
	) {}
}
