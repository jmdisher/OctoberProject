package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.jeffdisher.october.mutations.EntityChangeDoNothing;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.IMutableMinimalEntity;
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
	public static final long MINIMUM_TICKS_TO_NEW_ACTION = 10L;

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
	 * @param millisSinceLastTick Milliseconds based since last tick.
	 * @param changesToRun The map of changes to run in this tick, keyed by the ID of the creature on which they are
	 * scheduled.
	 * @return The subset of the changesToRun work which was completed by this thread.
	 */
	public static CreatureGroup processCreatureGroupParallel(ProcessorElement processor
			, Map<Integer, CreatureEntity> creaturesById
			, TickProcessingContext context
			, long millisSinceLastTick
			, Map<Integer, List<IMutationEntity<IMutableMinimalEntity>>> changesToRun
	)
	{
		Map<Integer, CreatureEntity> updatedCreatures = new HashMap<>();
		List<Integer> deadCreatureIds = new ArrayList<>();
		int committedMutationCount = 0;
		Random random = new Random(context.currentTick);
		for (Map.Entry<Integer, CreatureEntity> elt : creaturesById.entrySet())
		{
			if (processor.handleNextWorkUnit())
			{
				// This is our element.
				Integer id = elt.getKey();
				CreatureEntity creature = elt.getValue();
				processor.creaturesProcessed += 1;
				
				List<IMutationEntity<IMutableMinimalEntity>> changes = changesToRun.get(id);
				if (null == changes)
				{
					changes = List.of();
				}
				
				long millisApplied = 0L;
				MutableCreature mutable = MutableCreature.existing(creature);
				for (IMutationEntity<IMutableMinimalEntity> change : changes)
				{
					processor.creatureChangesProcessed += 1;
					boolean didApply = change.applyChange(context, mutable);
					if (didApply)
					{
						committedMutationCount += 1;
					}
					millisApplied += change.getTimeCostMillis();
				}
				
				// See if we should do something.
				if ((0L == millisApplied) && (context.currentTick > (mutable.newLastActionGameTick + MINIMUM_TICKS_TO_NEW_ACTION)))
				{
					long millisToMove = Math.min(millisSinceLastTick, EntityChangeMove.LIMIT_COST_MILLIS);
					float distanceToMove = (float)millisToMove * (EntityChangeMove.ENTITY_MOVE_FLAT_LIMIT_PER_SECOND / 1000.0f);
					// For now, we will just try to move in a random direction.
					int number = Math.abs(random.nextInt()) % 4;
					EntityChangeMove<IMutableMinimalEntity> move;
					switch (number)
					{
					case 0:
						move = new EntityChangeMove<>(mutable.newLocation, distanceToMove, 0.0f);
						break;
					case 1:
						move = new EntityChangeMove<>(mutable.newLocation, -distanceToMove, 0.0f);
						break;
					case 2:
						move = new EntityChangeMove<>(mutable.newLocation, 0.0f, distanceToMove);
						break;
					case 3:
						move = new EntityChangeMove<>(mutable.newLocation, 0.0f, -distanceToMove);
						break;
						default:
							throw Assert.unreachable();
					}
					mutable.newLastActionGameTick = context.currentTick;
					
					boolean didApply = move.applyChange(context, mutable);
					if (didApply)
					{
						committedMutationCount += 1;
					}
					millisApplied += millisToMove;
				}
				
				// See if we need to account for doing nothing.
				if (millisApplied < millisSinceLastTick)
				{
					long millisToWait = millisSinceLastTick - millisApplied;
					EntityChangeDoNothing<IMutableMinimalEntity> doNothing = new EntityChangeDoNothing<>(mutable.newLocation, millisToWait);
					boolean didApply = doNothing.applyChange(context, mutable);
					if (didApply)
					{
						committedMutationCount += 1;
					}
				}
				
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
		);
	}


	public static record CreatureGroup(int committedMutationCount
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}
}
