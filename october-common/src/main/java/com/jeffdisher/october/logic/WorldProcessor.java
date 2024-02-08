package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
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
	 * @param listener Receives callbacks (on the calling thread) related to block changes.
	 * @param loader Used to resolve read-only block data from the previous tick.
	 * @param gameTick The game tick being processed.
	 * @param mutationsToRun The map of mutations to run in this tick, keyed by cuboid addresses where they are
	 * scheduled.
	 * @return The subset of the mutationsToRun work which was completed by this thread.
	 */
	public static ProcessedFragment processWorldFragmentParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, IBlockChangeListener listener
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, Map<CuboidAddress, Queue<IMutationBlock>> mutationsToRun
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		List<IMutationBlock> exportedMutations = new ArrayList<>();
		Map<Integer, Queue<IMutationEntity>> exportedEntityChanges = new HashMap<>();
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
				Queue<IMutationEntity> entityChanges = exportedEntityChanges.get(targetEntityId);
				if (null == entityChanges)
				{
					entityChanges = new LinkedList<>();
					exportedEntityChanges.put(targetEntityId, entityChanges);
				}
				entityChanges.add(change);
			}
		};
		TickProcessingContext context = new TickProcessingContext(gameTick, loader, sink, newChangeSink);
		
		// Each thread will walk the map of mutations to run, each taking an entry and processing that cuboid.
		for (Map.Entry<CuboidAddress, Queue<IMutationBlock>> elt : mutationsToRun.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				CuboidAddress key = elt.getKey();
				Queue<IMutationBlock> mutations = elt.getValue();
				IReadOnlyCuboidData oldState = worldMap.get(key);
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != oldState);
				// We will accumulate changing blocks and determine if we need to write any back at the end.
				Map<BlockAddress, MutableBlockProxy> proxies = new HashMap<>();
				for (IMutationBlock mutation : mutations)
				{
					processor.mutationCount += 1;
					BlockAddress address = mutation.getAbsoluteLocation().getBlockAddress();
					MutableBlockProxy thisBlockProxy = proxies.get(address);
					if (null == thisBlockProxy)
					{
						thisBlockProxy = new MutableBlockProxy(address, oldState);
						proxies.put(address, thisBlockProxy);
					}
					boolean didApply = mutation.applyMutation(context, thisBlockProxy);
					if (didApply)
					{
						listener.mutationApplied(mutation);
					}
					else
					{
						listener.mutationDropped(mutation);
					}
				}
				
				// Return the old instance if nothing changed.
				List<MutableBlockProxy> proxiesToWrite = new ArrayList<>();
				for (MutableBlockProxy proxy : proxies.values())
				{
					if (proxy.didChange())
					{
						proxiesToWrite.add(proxy);
					}
				}
				if (proxiesToWrite.isEmpty())
				{
					// There were no actual changes to this cuboid so just use the old state.
					fragment.put(key, oldState);
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
				}
			}
		}
		// We package up any of the work that we did (note that no thread will return a cuboid which had no mutations in its fragment).
		return new ProcessedFragment(fragment, exportedMutations, exportedEntityChanges);
	}


	public static record ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, List<IMutationBlock> exportedMutations
			, Map<Integer, Queue<IMutationEntity>> exportedEntityChanges
	) {}


	public interface IBlockChangeListener
	{
		void mutationApplied(IMutationBlock mutation);
		void mutationDropped(IMutationBlock mutation);
	}
}
