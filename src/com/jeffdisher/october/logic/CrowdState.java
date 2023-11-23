package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import com.jeffdisher.october.types.Entity;


/**
 * Similar to WorldState, for the cuboids within the world, this is a snapshot of the state of all entities in the
 * system and the parallel processing logic for them.
 */
public class CrowdState
{
	private final Map<Integer, EntityWrapper> _entitiesById;

	public CrowdState(Map<Integer, EntityWrapper> entitiesById)
	{
		_entitiesById = Collections.unmodifiableMap(entitiesById);
	}

	public ProcessedGroup buildNewCrowdParallel(ProcessorElement processor)
	{
		Map<Integer, EntityWrapper> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		for (EntityWrapper wrapper : _entitiesById.values())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				EntityWrapper newWrapper;
				if (wrapper.changes.isEmpty())
				{
					// Nothing is changing so just copy this forward.
					newWrapper = wrapper;
				}
				else
				{
					// Something is changing so we need to build the mutable copy to modify.
					Consumer<IMutation> newMutationSink = new Consumer<>() {
						@Override
						public void accept(IMutation arg0)
						{
							exportedMutations.add(arg0);
						}
					};
					
					for (IEntityChange change : wrapper.changes())
					{
						processor.changeCount += 1;
						boolean didApply = change.applyChange(newMutationSink);
						// TODO:  Determine if we need a listener for didApply.
					}
					// TODO:  Handle the cases where the entity change actually changes the entity instead of just delivering a mutation.
					newWrapper = new EntityWrapper(wrapper.entity, new LinkedList<>());
				}
				fragment.put(wrapper.entity.id(), newWrapper);
			}
		}
		return new ProcessedGroup(fragment, exportedMutations);
	}


	public static record ProcessedGroup(Map<Integer, EntityWrapper> groupFragment
			, List<IMutation> exportedMutations
	) {}

	public static record EntityWrapper(Entity entity, Queue<IEntityChange> changes) {}
}
