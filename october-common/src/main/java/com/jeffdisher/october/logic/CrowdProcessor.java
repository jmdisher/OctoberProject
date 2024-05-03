package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.mutations.IMutationEntity;
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

	/**
	 * Applies the given changesToRun to the data in entitiesById, returning updated entities for some subset of the
	 * changes (previous entity instances will be returned if not changed).
	 * Note that this is expected to be run in parallel, across many threads, and will rely on a bakery algorithm to
	 * select each thread's subset of the work, dynamically.  The groups returned by all threads will have no overlap
	 * and the union of all of them will entirely cover the key space defined by changesToRun.
	 * 
	 * @param processor The current thread.
	 * @param entitiesById The map of all read-only entities from the previous tick.
	 * @param context The context used for running changes.
	 * @param millisSinceLastTick Milliseconds based since last tick.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the entity on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static ProcessedGroup processCrowdGroupParallel(ProcessorElement processor
			, Map<Integer, Entity> entitiesById
			, TickProcessingContext context
			, long millisSinceLastTick
			, Map<Integer, List<ScheduledChange>> changesToRun
	)
	{
		Map<Integer, Entity> fragment = new HashMap<>();
		
		Map<Integer, Entity> updatedEntities = new HashMap<>();
		Map<Integer, List<ScheduledChange>> delayedChanges = new HashMap<>();
		int committedMutationCount = 0;
		for (Map.Entry<Integer, List<ScheduledChange>> elt : changesToRun.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				Entity entity = entitiesById.get(id);
				
				// Note that the entity may have been unloaded.
				if (null != entity)
				{
					processor.entitiesProcessed += 1;
					
					MutableEntity mutable = MutableEntity.existing(entity);
					List<ScheduledChange> changes = elt.getValue();
					for (ScheduledChange scheduled : changes)
					{
						long millisUntilReady = scheduled.millisUntilReady();
						IMutationEntity change = scheduled.change();
						if (0L == millisUntilReady)
						{
							processor.entityChangesProcessed += 1;
							boolean didApply = change.applyChange(context, mutable);
							if (didApply)
							{
								committedMutationCount += 1;
							}
						}
						else
						{
							long updatedMillis = millisUntilReady - millisSinceLastTick;
							if (updatedMillis < 0L)
							{
								updatedMillis = 0L;
							}
							if (!delayedChanges.containsKey(id))
							{
								delayedChanges.put(id, new ArrayList<>());
							}
							List<ScheduledChange> list = delayedChanges.get(id);
							list.add(new ScheduledChange(change, updatedMillis));
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
		}
		Map<Integer, List<ScheduledChange>> notYetReadyChanges = new HashMap<>();
		for (Map.Entry<Integer, List<ScheduledChange>> elt : delayedChanges.entrySet())
		{
			Integer id = elt.getKey();
			List<ScheduledChange> incoming = elt.getValue();
			List<ScheduledChange> existing = notYetReadyChanges.get(id);
			if (null == existing)
			{
				notYetReadyChanges.put(id, incoming);
			}
			else
			{
				existing.addAll(incoming);
			}
		}
		return new ProcessedGroup(fragment
				, notYetReadyChanges
				, updatedEntities
				, committedMutationCount
		);
	}


	public static record ProcessedGroup(Map<Integer, Entity> groupFragment
			, Map<Integer, List<ScheduledChange>> notYetReadyChanges
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, Entity> updatedEntities
			, int committedMutationCount
	) {}
}
