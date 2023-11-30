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
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Static logic which implements the parallel game tick logic when operating on entities within the world.
 * The counterpart to this, for cuboids, is WorldProcessor.
 */
public class CrowdProcessor
{
	private CrowdProcessor()
	{
		// This is just static logic.
	}

	public static ProcessedGroup buildNewCrowdParallel(ProcessorElement processor
			, Map<Integer, Entity> entitiesById
			, IEntityChangeListener listener
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, Map<Integer, Queue<IEntityChange>> changesToRun
	)
	{
		Map<Integer, Entity> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		List<IEntityChange> exportedChanges = new ArrayList<>();
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
		TickProcessingContext context = new TickProcessingContext(gameTick, loader, newMutationSink, newChangeSink);
		
		for (Map.Entry<Integer, Entity> elt : entitiesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				Entity entity = elt.getValue();
				Entity newEntity;
				Queue<IEntityChange> changes = changesToRun.get(id);
				if (null == changes)
				{
					// Nothing is changing so just copy this forward.
					newEntity = entity;
				}
				else
				{
					// Something is changing so we need to build the mutable copy to modify.
					MutableEntity mutable = new MutableEntity(entity);
					for (IEntityChange change : changes)
					{
						processor.changeCount += 1;
						boolean didApply = change.applyChange(context, mutable);
						if (didApply)
						{
							listener.entityChanged(change.getTargetId());
						}
						else
						{
							listener.changeDropped(change);
						}
					}
					newEntity = mutable.freeze();
				}
				fragment.put(id, newEntity);
			}
		}
		return new ProcessedGroup(fragment, exportedMutations, exportedChanges);
	}


	public static record ProcessedGroup(Map<Integer, Entity> groupFragment
			, List<IMutation> exportedMutations
			, List<IEntityChange> exportedChanges
	) {}


	public interface IEntityChangeListener
	{
		void entityChanged(int id);
		void changeDropped(IEntityChange change);
	}
}
