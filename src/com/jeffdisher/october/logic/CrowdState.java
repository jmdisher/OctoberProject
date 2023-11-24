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
		List<IEntityChange> exportedChanges = new ArrayList<>();
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
							exportedChanges.add(arg0);
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
		return new ProcessedGroup(fragment, exportedMutations, exportedChanges);
	}

	public Entity getEntity(int id)
	{
		return _entitiesById.get(id).entity;
	}


	public static record ProcessedGroup(Map<Integer, EntityWrapper> groupFragment
			, List<IMutation> exportedMutations
			, List<IEntityChange> exportedChanges
	) {}

	public static record EntityWrapper(Entity entity, Queue<IEntityChange> changes) {}

	public interface IEntityChangeListener
	{
		void entityChanged(int id);
		void changeDropped(IEntityChange change);
	}
}
