package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;


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

	public static ProcessedFragment buildNewWorldParallel(ProcessorElement processor
			, Map<CuboidAddress, IReadOnlyCuboidData> worldMap
			, IBlockChangeListener listener
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, Map<CuboidAddress, Queue<IMutation>> mutationsToRun
	)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		List<IEntityChange> exportedEntityChanges = new ArrayList<>();
		Consumer<IMutation> sink = new Consumer<IMutation>() {
			@Override
			public void accept(IMutation arg0)
			{
				// Note that it may be worth pre-filtering the mutations to eagerly schedule them against this cuboid but that seems like needless complexity.
				exportedMutations.add(arg0);
			}};
			
		Consumer<IEntityChange> newChangeSink = new Consumer<>() {
			@Override
			public void accept(IEntityChange arg0)
			{
				exportedEntityChanges.add(arg0);
			}
		};
		TickProcessingContext context = new TickProcessingContext(gameTick, loader, sink, newChangeSink);
		
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				CuboidAddress key = elt.getKey();
				IReadOnlyCuboidData oldState = elt.getValue();
				Queue<IMutation> mutations = mutationsToRun.get(key);
				IReadOnlyCuboidData newData;
				if (null == mutations)
				{
					// Nothing is changing so we can just return the existing data.
					newData = oldState;
				}
				else
				{
					// Something is changing so we need to build the mutable copy to modify.
					CuboidData mutable = CuboidData.mutableClone(oldState);
					for (IMutation mutation : mutations)
					{
						processor.mutationCount += 1;
						AbsoluteLocation absolteLocation = mutation.getAbsoluteLocation();
						MutableBlockProxy thisBlockProxy = new MutableBlockProxy(absolteLocation.getBlockAddress(), mutable);
						boolean didApply = mutation.applyMutation(context, thisBlockProxy);
						if (didApply)
						{
							listener.blockChanged(absolteLocation);
						}
						else
						{
							listener.mutationDropped(mutation);
						}
					}
					newData = mutable;
				}
				fragment.put(key, newData);
			}
		}
		return new ProcessedFragment(fragment, exportedMutations, exportedEntityChanges);
	}


	public static record ProcessedFragment(Map<CuboidAddress, IReadOnlyCuboidData> stateFragment
			, List<IMutation> exportedMutations
			, List<IEntityChange> exportedEntityChanges
	) {}


	public interface IBlockChangeListener
	{
		void blockChanged(AbsoluteLocation location);
		void mutationDropped(IMutation mutation);
	}
}
