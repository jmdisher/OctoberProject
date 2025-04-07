package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static logic which implements the parallel game tick logic when operating on CreatureEntity instances within the
 * world.
 * This is very similar to CrowdProcessor but has some different internal logic and other helpers related to creatures.
 */
public class CreatureProcessor
{
	private CreatureProcessor()
	{
		// This is just static logic.
	}

	/**
	 * Runs the given changesToRun on the creatures provided in creaturesById, in parallel.
	 * 
	 * @param processor The current thread.
	 * @param creaturesById The map of all read-only creatures from the previous tick.
	 * @param context The context used for running changes.
	 * @param entityCollection A look-up mechanism for the entities in the loaded world.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the creature on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static CreatureGroup processCreatureGroupParallel(ProcessorElement processor
			, Map<Integer, CreatureEntity> creaturesById
			, TickProcessingContext context
			, EntityCollection entityCollection
			, Map<Integer, List<IMutationEntity<IMutableCreatureEntity>>> changesToRun
	)
	{
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		for (Map.Entry<Integer, CreatureEntity> elt : creaturesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				CreatureEntity creature = elt.getValue();
				processor.creaturesProcessed += 1;
				
				MutableCreature mutable = MutableCreature.existing(creature);
				TickUtils.IFallDamage damageApplication = (int damage) ->{
					EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, mutable, (byte)damage, EventRecord.Cause.FALL);
				};
				
				// Determine if we need to schedule movements.
				List<IMutationEntity<IMutableCreatureEntity>> changes = changesToRun.get(id);
				long millisAtEndOfTick = context.millisPerTick;
				if (null != changes)
				{
					millisAtEndOfTick = _runExternalChanges(processor, context, damageApplication, mutable, changes, millisAtEndOfTick);
				}
				
				// Now that we have handled any normally queued up changes acting ON this creature, see if they want to do anything special.
				boolean didSpecial = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutable);
				
				// If we have any time left, see what other actions we can take.
				if (!didSpecial && (millisAtEndOfTick > 0L))
				{
					// If we didn't perform a special action, we can proceed with movement.
					millisAtEndOfTick = _runInternalChanges(processor, context, damageApplication, mutable, millisAtEndOfTick);
				}
				
				// Account for time passing.
				if (millisAtEndOfTick > 0L)
				{
					TickUtils.allowMovement(context.previousBlockLookUp, damageApplication, mutable, millisAtEndOfTick);
				}
				TickUtils.endOfTick(context, mutable);
				
				// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
				// This freeze() call will return the original instance if it is identical.
				// Note that the creature will become null if it died.
				CreatureEntity newEntity = mutable.freeze();
				if (null == newEntity)
				{
					deadCreatureIds.add(id);
				}
				else if (newEntity != creature)
				{
					updatedCreatures.put(id, newEntity);
				}
			}
		}
		return new CreatureGroup(false
				, updatedCreatures
				, deadCreatureIds
		);
	}


	private static long _runExternalChanges(ProcessorElement processor
			, TickProcessingContext context
			, TickUtils.IFallDamage damageApplication
			, MutableCreature mutable
			, List<IMutationEntity<IMutableCreatureEntity>> changes
			, long millisAtEndOfTick
	)
	{
		for (IMutationEntity<IMutableCreatureEntity> change : changes)
		{
			processor.creatureChangesProcessed += 1;
			boolean didApply = change.applyChange(context, mutable);
			if (didApply)
			{
				// If this applied, account for time passing.
				long millisInChange = change.getTimeCostMillis();
				if (millisInChange > 0L)
				{
					TickUtils.allowMovement(context.previousBlockLookUp, damageApplication, mutable, millisInChange);
					millisAtEndOfTick -= millisInChange;
				}
			}
		}
		return millisAtEndOfTick;
	}

	private static long _runInternalChanges(ProcessorElement processor
			, TickProcessingContext context
			, TickUtils.IFallDamage damageApplication
			, MutableCreature mutable
			, long millisAtEndOfTick
	)
	{
		boolean canSchedule = true;
		while (canSchedule)
		{
			// Note that this may still return a null list of next steps if there is nothing to do.
			IMutationEntity<IMutableCreatureEntity> change = CreatureLogic.planNextAction(context, mutable, millisAtEndOfTick);
			if (null != change)
			{
				long timeCostMillis = change.getTimeCostMillis();
				// This must be able to fit into a single tick.
				Assert.assertTrue(timeCostMillis <= context.millisPerTick);
				if (timeCostMillis <= millisAtEndOfTick)
				{
					processor.creatureChangesProcessed += 1;
					boolean didApply = change.applyChange(context, mutable);
					if (didApply)
					{
						// If this applied, account for time passing.
						if (timeCostMillis > 0L)
						{
							TickUtils.allowMovement(context.previousBlockLookUp, damageApplication, mutable, timeCostMillis);
						}
					}
					millisAtEndOfTick -= timeCostMillis;
					if ((0L == millisAtEndOfTick) || (0L == timeCostMillis))
					{
						canSchedule = false;
					}
				}
				else
				{
					// We have done all we can.
					canSchedule = false;
				}
			}
			else
			{
				// We have done all we can.
				canSchedule = false;
			}
		}
		return millisAtEndOfTick;
	}


	public static record CreatureGroup(boolean ignored
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}
}
