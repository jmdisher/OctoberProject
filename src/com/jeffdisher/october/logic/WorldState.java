package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
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
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.TickProcessingContext;


public class WorldState
{
	private final Map<CuboidAddress, IReadOnlyCuboidData> _worldMap;

	public WorldState(Map<CuboidAddress, IReadOnlyCuboidData> worldMap)
	{
		_worldMap = Collections.unmodifiableMap(worldMap);
	}

	public ProcessedFragment buildNewWorldParallel(ProcessorElement processor
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
		
		for (Map.Entry<CuboidAddress, IReadOnlyCuboidData> elt : _worldMap.entrySet())
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

	/**
	 * Creates a read-only proxy for accessing one of the blocks in the loaded world state.
	 * 
	 * @param location The xyz location of the block.
	 * @return The block copy or null if the location isn't loaded.
	 */
	public BlockProxy getBlockProxy(AbsoluteLocation location)
	{
		CuboidAddress address = location.getCuboidAddress();
		IReadOnlyCuboidData cuboid = _worldMap.get(address);
		
		BlockProxy block = null;
		if (null != cuboid)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = new BlockProxy(blockAddress, cuboid);
		}
		return block;
	}

	/**
	 * Creates and returns a function which will load read-only BlockProxy objects for locations in this world.  If the
	 * requested block is in a cuboid which isn't loaded, returns null.
	 * 
	 * @return The block loader function.
	 */
	public Function<AbsoluteLocation, BlockProxy> buildReadOnlyLoader()
	{
		return (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			IReadOnlyCuboidData cuboid = _worldMap.get(address);
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
			;
		};
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
