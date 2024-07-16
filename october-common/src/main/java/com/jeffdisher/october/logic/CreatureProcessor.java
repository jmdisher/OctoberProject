package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.mutations.EntityEndOfTick;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.CreatureEntity;
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
		long millisSinceLastTick = context.millisPerTick;
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		List<CreatureEntity> newlySpawnedCreatures = new ArrayList<>();
		Consumer<CreatureEntity> creatureSpawner = (CreatureEntity newCreature) -> {
			newlySpawnedCreatures.add(newCreature);
		};
		int committedMutationCount = 0;
		for (Map.Entry<Integer, CreatureEntity> elt : creaturesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				CreatureEntity creature = elt.getValue();
				processor.creaturesProcessed += 1;
				
				MutableCreature mutable = MutableCreature.existing(creature);
				
				// Determine if we need to schedule movements.
				List<IMutationEntity<IMutableCreatureEntity>> changes = changesToRun.get(id);
				if (null == changes)
				{
					// We have nothing to do, and nothing is acting on us, so see if we have a plan to apply or should select one.
					if (null == mutable.newStepsToNextMove)
					{
						// We have no immediate steps planned so ask the creature if it wants to take special actions or plan the next path
						long ticksSinceLastAction = context.currentTick - mutable.newLastActionGameTick;
						long millisSinceLastAction = context.millisPerTick * ticksSinceLastAction;
						// Note that this may still return a null list of next steps if there is nothing to do.
						mutable.newStepsToNextMove = CreatureLogic.planNextActions(context, creatureSpawner, entityCollection, millisSinceLastAction, millisSinceLastTick, mutable);
					}
					if (null != mutable.newStepsToNextMove)
					{
						// It is possible that we have an empty plan (just to reset the last move timer).
						if (mutable.newStepsToNextMove.isEmpty())
						{
							mutable.newStepsToNextMove = null;
							changes = List.of();
						}
						else
						{
							changes = _scheduleForThisTick(millisSinceLastTick, mutable);
						}
					}
					
				}
				
				if (null != changes)
				{
					for (IMutationEntity<IMutableCreatureEntity> change : changes)
					{
						processor.creatureChangesProcessed += 1;
						boolean didApply = change.applyChange(context, mutable);
						if (didApply)
						{
							committedMutationCount += 1;
						}
					}
					mutable.newLastActionGameTick = context.currentTick;
				}
				
				// Account for time passing.
				EntityEndOfTick end = new EntityEndOfTick(millisSinceLastTick);
				end.apply(context, mutable);
				
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
		return new CreatureGroup(committedMutationCount
				, updatedCreatures
				, deadCreatureIds
				, newlySpawnedCreatures
		);
	}


	private static List<IMutationEntity<IMutableCreatureEntity>> _scheduleForThisTick(long millisSinceLastTick, MutableCreature mutable)
	{
		// We are already on the way to the next step in our path so see what we can apply in this tick.
		List<IMutationEntity<IMutableCreatureEntity>> changes = new ArrayList<>();
		long millisRemaining = millisSinceLastTick;
		List<IMutationEntity<IMutableCreatureEntity>> remainingSteps = new ArrayList<>();
		boolean canAdd = true;
		for (IMutationEntity<IMutableCreatureEntity> check : mutable.newStepsToNextMove)
		{
			if (canAdd)
			{
				long timeCostMillis = check.getTimeCostMillis();
				// This must be able to fit into a single tick.
				Assert.assertTrue(timeCostMillis <= millisSinceLastTick);
				if (timeCostMillis <= millisRemaining)
				{
					changes.add(check);
					millisRemaining -= timeCostMillis;
				}
				else
				{
					// We have done all we can.
					canAdd = false;
				}
			}
			if (!canAdd)
			{
				remainingSteps.add(check);
			}
		}
		
		// We must have at least done something.
		Assert.assertTrue(!changes.isEmpty());
		if (remainingSteps.isEmpty())
		{
			mutable.newStepsToNextMove = null;
		}
		else
		{
			mutable.newStepsToNextMove = remainingSteps;
		}
		return changes;
	}


	public static record CreatureGroup(int committedMutationCount
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
			, List<CreatureEntity> newlySpawnedCreatures
	) {}
}
