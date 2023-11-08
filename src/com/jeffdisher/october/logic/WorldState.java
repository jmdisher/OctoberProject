package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.utils.Encoding;


public class WorldState
{
	private final Map<Long, CuboidState> _worldMap;

	public WorldState(Map<Long, CuboidState> worldMap)
	{
		// Note that we want to concurrently iterate the map and Collections.unmodifiableMap seems to have some lazy
		// initialization so we use a HashMap, which is apparently safe for concurrent reads.
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
					short[] cuboidAddress = newData.getCuboidAddress();
					CuboidState newState = new CuboidState(newData);
					Consumer<IMutation> sink = new Consumer<IMutation>() {
						@Override
						public void accept(IMutation arg0)
						{
							short[] address = Encoding.getCombinedCuboidAddress(arg0.getAbsoluteLocation());
							if ((cuboidAddress[0] == address[0])
									&& (cuboidAddress[1] == address[1])
									&& (cuboidAddress[2] == address[2])
							) {
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


	public static record ProcessedFragment(Map<Long, CuboidState> stateFragment, List<IMutation> exportedMutations)
	{
	}


	public interface IBlockChangeListener
	{
		void blockChanged(int[] absoluteLocation);
		void mutationDropped(IMutation mutation);
	}
}
