package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static logic which implements the parallel game tick logic when operating on entities within the world.
 * The counterpart to this, for cuboids, is WorldProcessor.
 */
public class CrowdProcessor
{
	/**
	 * The ID used for the operator "no entity" cases (that is, when the operator runs an action but not against any
	 * specific entity).
	 */
	public static final int OPERATOR_ENTITY_ID = Integer.MIN_VALUE;


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
	 * @param context The context used for running changes.
	 * @param entitiesById The map of all read-only entities and scheduled changes (may not be ready).
	 * @param operatorChanges The changes to run as the operator.
	 * @return The OutputEntity instances for InputEntity instances processed by this thread.
	 */
	public static ProcessedGroup processCrowdGroupParallel(ProcessorElement processor
			, TickProcessingContext context
			, Map<Integer, InputEntity> entitiesById
			, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
	)
	{
		Map<Integer, OutputEntity> processedEntities = new HashMap<>();
		int committedMutationCount = 0;
		
		// We need to check the operator as a special-case since it isn't a real entity.
		if (processor.handleNextWorkUnit())
		{
			// Verify that this isn't redundantly described.
			Assert.assertTrue(!entitiesById.containsKey(OPERATOR_ENTITY_ID));
			for (IEntityAction<IMutablePlayerEntity> change : operatorChanges)
			{
				change.applyChange(context, null);
			}
		}
		for (Map.Entry<Integer, InputEntity> elt : entitiesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				InputEntity input = elt.getValue();
				Entity entity = input.entity;
				processor.entitiesProcessed += 1;
				
				MutableEntity mutable = MutableEntity.existing(entity);
				float startZVelocity = mutable.newVelocity.z();
				List<ScheduledChange> changes = input.scheduledChanges;
				List<ScheduledChange> notYetReadyChanges = new ArrayList<>();
				for (ScheduledChange scheduled : changes)
				{
					long millisUntilReady = scheduled.millisUntilReady();
					IEntityAction<IMutablePlayerEntity> change = scheduled.change();
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
						notYetReadyChanges.add(new ScheduledChange(change, updatedMillis));
					}
				}
				
				byte fallDamage = TickUtils.calculateFallDamage(startZVelocity - mutable.newVelocity.z());
				if (fallDamage > 0)
				{
					DamageHelpers.applyDamageDirectlyAndPostEvent(context, mutable, (byte)fallDamage, EventRecord.Cause.FALL);
				}
				// Account for time passing.
				TickUtils.endOfTick(context, mutable);
				
				// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
				// This freeze() call will return the original instance if it is identical.
				Entity newEntity = mutable.freeze();
				Entity entityToReturn = (newEntity != entity)
					? newEntity
					: null
				;
				processedEntities.put(id, new OutputEntity(entityToReturn, notYetReadyChanges));
			}
		}
		return new ProcessedGroup(committedMutationCount
			, processedEntities
		);
	}


	public static record ProcessedGroup(int committedMutationCount
		// We will pass back an OutputEntity for every InputEntity processed by this thread, even if no changes.
		, Map<Integer, OutputEntity> entityOutput
	) {}

	// Note that NEITHER of these will be NULL and scheduledChanges MUST not be empty.
	public static record InputEntity(Entity entity
		, List<ScheduledChange> scheduledChanges
	) {}

	// Note that "entity" will be NULL if unchanged and notYetReadyChanges will NEVER be NULL but may be empty.
	public static record OutputEntity(Entity entity
		, List<ScheduledChange> notYetReadyChanges
	) {}
}
