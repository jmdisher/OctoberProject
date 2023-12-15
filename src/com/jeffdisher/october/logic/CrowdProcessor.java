package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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
import com.jeffdisher.october.utils.Assert;


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

	/**
	 * Applies the given changesToRun to the data in entitiesById, returning updated entities for some subset of the
	 * changes.
	 * Note that this is expected to be run in parallel, across many threads, and will rely on a bakery algorithm to
	 * select each thread's subset of the work, dynamically.  The groups returned by all threads will have no overlap
	 * and the union of all of them will entirely cover the key space defined by changesToRun.
	 * 
	 * @param processor The current thread.
	 * @param entitiesById The map of all read-only entities from the previous tick.
	 * @param listener Receives callbacks (on the calling thread) related to entity changes.
	 * @param loader Used to resolve read-only block data from the previous tick.
	 * @param gameTick The game tick being processed.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the entity on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static ProcessedGroup processCrowdGroupParallel(ProcessorElement processor
			, Map<Integer, Entity> entitiesById
			, IEntityChangeListener listener
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, Map<Integer, Queue<IEntityChange>> changesToRun
	)
	{
		Map<Integer, Entity> fragment = new HashMap<>();
		List<IMutation> exportedMutations = new ArrayList<>();
		Map<Integer, Queue<IEntityChange>> exportedChanges = new HashMap<>();
		Consumer<IMutation> newMutationSink = new Consumer<>() {
			@Override
			public void accept(IMutation arg0)
			{
				exportedMutations.add(arg0);
			}
		};
		TickProcessingContext.IChangeSink newChangeSink = new TickProcessingContext.IChangeSink() {
			@Override
			public void accept(int targetEntityId, IEntityChange change)
			{
				Queue<IEntityChange> entityChanges = exportedChanges.get(targetEntityId);
				if (null == entityChanges)
				{
					entityChanges = new LinkedList<>();
					exportedChanges.put(targetEntityId, entityChanges);
				}
				entityChanges.add(change);
			}
		};
		// We will use null for twoPhaseChangeSink since the mutations we were given will intercept this where relevant.
		TickProcessingContext context = new TickProcessingContext(gameTick, loader, newMutationSink, newChangeSink, null);
		
		for (Map.Entry<Integer, Queue<IEntityChange>> elt : changesToRun.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				Queue<IEntityChange> changes = elt.getValue();
				Entity entity = entitiesById.get(id);
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != entity);
				MutableEntity mutable = new MutableEntity(entity);
				for (IEntityChange change : changes)
				{
					processor.changeCount += 1;
					boolean didApply = change.applyChange(context, mutable);
					if (didApply)
					{
						listener.changeApplied(id, change);
					}
					else
					{
						listener.changeDropped(id, change);
					}
				}
				Entity newEntity = mutable.freeze();
				fragment.put(id, newEntity);
			}
		}
		return new ProcessedGroup(fragment, exportedMutations, exportedChanges);
	}


	public static record ProcessedGroup(Map<Integer, Entity> groupFragment
			, List<IMutation> exportedMutations
			, Map<Integer, Queue<IEntityChange>> exportedChanges
	) {}


	public interface IEntityChangeListener
	{
		void changeApplied(int targetEntityId, IEntityChange change);
		void changeDropped(int targetEntityId, IEntityChange change);
	}
}
