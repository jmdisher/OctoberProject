package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
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
				float startZVelocity = mutable.newVelocity.z();
				
				// Determine if we need to schedule movements.
				List<IMutationEntity<IMutableCreatureEntity>> changes = changesToRun.get(id);
				if (null != changes)
				{
					_runExternalChanges(processor, context, mutable, changes);
				}
				
				// Now that we have handled any normally queued up changes acting ON this creature, see if they want to do anything special.
				boolean didSpecial = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutable);
				if (!didSpecial)
				{
					// If we didn't perform a special action, we can proceed with movement.
					_runInternalChanges(processor, context, mutable, context.millisPerTick);
				}
				else
				{
					EntityChangeTopLevelMovement<IMutableCreatureEntity> change = _createStandingChange(context, mutable);
					boolean didApply = change.applyChange(context, mutable);
					// We just asked to create this so failure doesn't make sense.
					Assert.assertTrue(didApply);
				}
				byte fallDamage = TickUtils.calculateFallDamage(startZVelocity - mutable.newVelocity.z());
				if (fallDamage > 0)
				{
					EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, mutable, (byte)fallDamage, EventRecord.Cause.FALL);
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


	private static void _runExternalChanges(ProcessorElement processor
			, TickProcessingContext context
			, MutableCreature mutable
			, List<IMutationEntity<IMutableCreatureEntity>> changes
	)
	{
		for (IMutationEntity<IMutableCreatureEntity> change : changes)
		{
			processor.creatureChangesProcessed += 1;
			// These external changes should all take 0 millis.
			long millisInChange = change.getTimeCostMillis();
			Assert.assertTrue(0L == millisInChange);
			
			// Note that we ignore this response since it can fail.
			change.applyChange(context, mutable);
		}
	}

	private static void _runInternalChanges(ProcessorElement processor
			, TickProcessingContext context
			, MutableCreature mutable
			, long millisAtEndOfTick
	)
	{
		// Note that this may still return a null list of next steps if there is nothing to do.
		EntityChangeTopLevelMovement<IMutableCreatureEntity> change = CreatureLogic.planNextAction(context, mutable, millisAtEndOfTick);
		if (null == change)
		{
			// In this case, we just want to synthesize a "do nothing" standing action so that we fall, etc.
			change = _createStandingChange(context, mutable);
		}
		processor.creatureChangesProcessed += 1;
		boolean didApply = change.applyChange(context, mutable);
		// We just asked to create this so failure doesn't make sense.
		Assert.assertTrue(didApply);
	}

	private static EntityChangeTopLevelMovement<IMutableCreatureEntity> _createStandingChange(TickProcessingContext context
			, MutableCreature mutable
	)
	{
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		float viscosity = EntityMovementHelpers.maxViscosityInEntityBlocks(mutable.newLocation, mutable.getType().volume(), context.previousBlockLookUp);
		return CreatureMovementHelpers.buildStandingChange(reader, mutable.newLocation, mutable.newVelocity, mutable.newYaw, mutable.newPitch, mutable.getType(), context.millisPerTick, viscosity);
	}


	public static record CreatureGroup(boolean ignored
			// Note that we will only pass back a new Entity object if it changed.
			, Map<Integer, CreatureEntity> updatedCreatures
			, List<Integer> deadCreatureIds
	) {}
}
