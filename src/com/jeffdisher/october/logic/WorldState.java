package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.data.Block;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class WorldState
{
	private final Map<Long, CuboidState> _worldMap;

	public WorldState(Map<Long, CuboidState> worldMap)
	{
		_worldMap = Collections.unmodifiableMap(worldMap);
	}

	public ProcessedFragment buildNewWorldParallel(ProcessorElement processor, IBlockChangeListener listener)
	{
		Map<Long, CuboidState> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		for (Map.Entry<Long, CuboidState> elt : _worldMap.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Long key = elt.getKey();
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
					for (IMutation mutation : mutations)
					{
						processor.mutationCount += 1;
						boolean didApply = mutation.applyMutation(this, newData, sink);
						if (didApply)
						{
							listener.blockChanged(mutation.getAbsoluteLocation());
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
		return new ProcessedFragment(fragment, exportedMutations);
	}

	/**
	 * Copies out all aspects of the block at the given absolute location, returning null if it is in an unloaded
	 * cuboid.
	 * 
	 * @param location The xyz location of the block.
	 * @return The block copy or null if the location isn't loaded.
	 */
	public Block getBlock(AbsoluteLocation location)
	{
		CuboidAddress address = location.getCuboidAddress();
		long hash = address.getLongHash();
		CuboidState cuboid = _worldMap.get(hash);
		
		Block block = null;
		if (null != cuboid)
		{
			BlockAddress blockAddress = location.getBlockAddress();
			block = cuboid.data.getBlock(blockAddress);
		}
		return block;
	}


	public static record ProcessedFragment(Map<Long, CuboidState> stateFragment, List<IMutation> exportedMutations)
	{
	}


	public interface IBlockChangeListener
	{
		void blockChanged(AbsoluteLocation location);
		void mutationDropped(IMutation mutation);
	}
}
