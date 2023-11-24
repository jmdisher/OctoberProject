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
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.utils.Assert;


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

	public ProcessedGroup buildNewCrowdParallel(ProcessorElement processor, IEntityChangeListener listener)
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
					MutableEntity mutable = new MutableEntity(wrapper.entity);
					Consumer<IMutation> newMutationSink = new Consumer<>() {
						@Override
						public void accept(IMutation arg0)
						{
							exportedMutations.add(arg0);
						}
					};
					Consumer<IEntityChange> newChangeSink = new Consumer<>() {
						@Override
						public void accept(IEntityChange arg0)
						{
							// TODO: Pass these back - this change is just a stop-gap until we start using this.
							Assert.unreachable();
						}
					};
					
					for (IEntityChange change : wrapper.changes())
					{
						processor.changeCount += 1;
						boolean didApply = change.applyChange(mutable, newMutationSink, newChangeSink);
						if (didApply)
						{
							listener.entityChanged(change.getTargetId());
						}
						else
						{
							listener.changeDropped(change);
						}
					}
					newWrapper = new EntityWrapper(mutable.freeze(), new LinkedList<>());
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

	public interface IEntityChangeListener
	{
		void entityChanged(int id);
		void changeDropped(IEntityChange change);
	}
}
