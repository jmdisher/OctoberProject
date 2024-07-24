package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
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
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the entity on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static ProcessedGroup processCrowdGroupParallel(ProcessorElement processor
			, Map<Integer, Entity> entitiesById
			, TickProcessingContext context
			, Map<Integer, List<ScheduledChange>> changesToRun
	)
	{
		Map<Integer, Entity> updatedEntities = new HashMap<>();
		Map<Integer, List<ScheduledChange>> delayedChanges = new HashMap<>();
		int committedMutationCount = 0;
		for (Map.Entry<Integer, Entity> elt : entitiesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				Entity entity = elt.getValue();
				processor.entitiesProcessed += 1;
				
				MutableEntity mutable = MutableEntity.existing(entity);
				List<ScheduledChange> changes = changesToRun.get(id);
				if (null != changes)
				{
					for (ScheduledChange scheduled : changes)
					{
						long millisUntilReady = scheduled.millisUntilReady();
						IMutationEntity<IMutablePlayerEntity> change = scheduled.change();
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
							long updatedMillis = millisUntilReady - context.millisPerTick;
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
				}
				
				// Account for time passing.
				long millisAtEndOfTick = context.millisPerTick;
				if (millisAtEndOfTick > 0L)
				{
					TickUtils.allowMovement(context.previousBlockLookUp, mutable, millisAtEndOfTick);
				}
				TickUtils.endOfTick(context, mutable);
				
				// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
				// This freeze() call will return the original instance if it is identical.
				Entity newEntity = mutable.freeze();
				if (newEntity != entity)
				{
					updatedEntities.put(id, newEntity);
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
		return new ProcessedGroup(committedMutationCount
				, notYetReadyChanges
				, updatedEntities
		);
	}


	public static record ProcessedGroup(int committedMutationCount
			, Map<Integer, List<ScheduledChange>> notYetReadyChanges
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, Entity> updatedEntities
	) {}
}
