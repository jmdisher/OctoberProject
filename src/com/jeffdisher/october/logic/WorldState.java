package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class WorldState
{
	private final Map<CuboidAddress, CuboidState> _worldMap;

	public WorldState(Map<CuboidAddress, CuboidState> worldMap)
	{
		_worldMap = Collections.unmodifiableMap(worldMap);
	}

	public ProcessedFragment buildNewWorldParallel(ProcessorElement processor, IBlockChangeListener listener)
	{
		Function<AbsoluteLocation, BlockProxy> oldWorldLoader = (AbsoluteLocation location) -> {
			CuboidAddress address = location.getCuboidAddress();
			CuboidState cuboid = _worldMap.get(address);
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid.data)
					: null
			;
		};
		Map<CuboidAddress, CuboidState> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		List<IEntityChange> exportedEntityChanges = new ArrayList<>();
		for (Map.Entry<CuboidAddress, CuboidState> elt : _worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				CuboidAddress key = elt.getKey();
				CuboidState oldState = elt.getValue();
				List<IMutation> mutations = oldState.drainPendingMutations();
				if (null == mutations)
				{
					// Nothing is changing so we can just return the existing data.
					fragment.put(key, oldState);
				}
				else
				{
					// Something is changing so we need to build the mutable copy to modify.
					CuboidData newData = CuboidData.mutableClone(oldState.data);
					CuboidAddress cuboidAddress = newData.getCuboidAddress();
					CuboidState newState = new CuboidState(newData);
					Consumer<IMutation> sink = new Consumer<IMutation>() {
						@Override
						public void accept(IMutation arg0)
						{
							CuboidAddress address = arg0.getAbsoluteLocation().getCuboidAddress();
							if (cuboidAddress.equals(address))
							{
								newState.enqueueMutation(arg0);
							}
							else
							{
								exportedMutations.add(arg0);
							}
						}};
						
					Consumer<IEntityChange> newChangeSink = new Consumer<>() {
						@Override
						public void accept(IEntityChange arg0)
						{
							exportedEntityChanges.add(arg0);
						}
					};
					for (IMutation mutation : mutations)
					{
						processor.mutationCount += 1;
						AbsoluteLocation absolteLocation = mutation.getAbsoluteLocation();
						MutableBlockProxy thisBlockProxy = new MutableBlockProxy(absolteLocation.getBlockAddress(), newData);
						boolean didApply = mutation.applyMutation(oldWorldLoader, thisBlockProxy, sink, newChangeSink);
						if (didApply)
						{
							listener.blockChanged(absolteLocation);
						}
						else
						{
							listener.mutationDropped(mutation);
						}
					}
					fragment.put(key, newState);
				}
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
		CuboidState cuboid = _worldMap.get(address);
		
		BlockProxy block = null;
		if (null != cuboid)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = new BlockProxy(blockAddress, cuboid.data);
		}
		return block;
	}


	public static record ProcessedFragment(Map<CuboidAddress, CuboidState> stateFragment
			, List<IMutation> exportedMutations
			, List<IEntityChange> exportedEntityChanges
	) {}


	public interface IBlockChangeListener
	{
		void blockChanged(AbsoluteLocation location);
		void mutationDropped(IMutation mutation);
	}
}
