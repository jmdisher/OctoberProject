package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.IMutationEntity;
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
	 * changes (previous entity instances will be returned if not changed).
	 * Note that this is expected to be run in parallel, across many threads, and will rely on a bakery algorithm to
	 * select each thread's subset of the work, dynamically.  The groups returned by all threads will have no overlap
	 * and the union of all of them will entirely cover the key space defined by changesToRun.
	 * 
	 * @param processor The current thread.
	 * @param entitiesById The map of all read-only entities from the previous tick.
	 * @param loader Used to resolve read-only block data from the previous tick.
	 * @param gameTick The game tick being processed.
	 * @param millisSinceLastTick Milliseconds based since last tick.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the entity on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static ProcessedGroup processCrowdGroupParallel(ProcessorElement processor
			, Map<Integer, Entity> entitiesById
			, Function<AbsoluteLocation, BlockProxy> loader
			, long gameTick
			, long millisSinceLastTick
			, Map<Integer, List<IMutationEntity>> changesToRun
	)
	{
		Map<Integer, Entity> fragment = new HashMap<>();
		
		CommonMutationSink newMutationSink = new CommonMutationSink();
		CommonChangeSink newChangeSink = new CommonChangeSink();
		
		Map<Integer, Entity> updatedEntities = new HashMap<>();
		int committedMutationCount = 0;
		for (Map.Entry<Integer, List<IMutationEntity>> elt : changesToRun.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				List<IMutationEntity> changes = elt.getValue();
				Entity entity = entitiesById.get(id);
				
				BasicBlockProxyCache local = new BasicBlockProxyCache(loader);
				TickProcessingContext context = new TickProcessingContext(gameTick, local, newMutationSink, newChangeSink);
				
				// We can't be told to operate on something which isn't in the state.
				Assert.assertTrue(null != entity);
				MutableEntity mutable = MutableEntity.existing(entity);
				for (IMutationEntity change : changes)
				{
					processor.changeCount += 1;
					boolean didApply = change.applyChange(context, mutable);
					if (didApply)
					{
						committedMutationCount += 1;
					}
				}
				
				// Return the old instance if nothing changed.
				// This freeze() call will return the original instance if it is identical.
				Entity newEntity = mutable.freeze();
				fragment.put(id, newEntity);
				
				// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
				if (newEntity != entity)
				{
					updatedEntities.put(id, newEntity);
				}
			}
		}
		List<ScheduledMutation> exportedMutations = newMutationSink.takeExportedMutations();
		Map<Integer, List<IMutationEntity>> exportedChanges = newChangeSink.takeExportedChanges();
		return new ProcessedGroup(fragment
				, exportedMutations
				, exportedChanges
				, updatedEntities
				, committedMutationCount
		);
	}


	public static record ProcessedGroup(Map<Integer, Entity> groupFragment
			, List<ScheduledMutation> exportedMutations
			, Map<Integer, List<IMutationEntity>> exportedChanges
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, Entity> updatedEntities
			, int committedMutationCount
	) {}
}
