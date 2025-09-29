package com.jeffdisher.october.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.logic.CreatureMovementHelpers;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.ProcessorElement;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Static engine logic related to processing creature entities in the world.
 */
public class EngineCreatures
{
	private EngineCreatures()
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
			, Map<Integer, List<IEntityAction<IMutableCreatureEntity>>> changesToRun
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
				List<IEntityAction<IMutableCreatureEntity>> changes = changesToRun.get(id);
				processor.creaturesProcessed += 1;
				if (null != changes)
				{
					processor.creatureChangesProcessed += changes.size();
				}
				SingleCreatureResult result = _processOneCreature(context, entityCollection, creature, changes);
				if (null == result.updatedEntity)
				{
					deadCreatureIds.add(id);
				}
				else if (result.updatedEntity != creature)
				{
					updatedCreatures.put(id, result.updatedEntity);
				}
				if (!result.didTakeSpecialAction)
				{
					processor.creatureChangesProcessed += 1;
				}
			}
		}
		return new CreatureGroup(false
				, updatedCreatures
				, deadCreatureIds
		);
	}


	// Returns an updated creature or null, if it died.
	private static SingleCreatureResult _processOneCreature(TickProcessingContext context
		, EntityCollection entityCollection
		, CreatureEntity creature
		, List<IEntityAction<IMutableCreatureEntity>> changesToRun
	)
	{
		MutableCreature mutable = MutableCreature.existing(creature);
		float startZVelocity = mutable.newVelocity.z();
		
		// Determine if we need to schedule movements.
		if (null != changesToRun)
		{
			_runExternalChanges(context, mutable, changesToRun);
		}
		
		// Now that we have handled any normally queued up changes acting ON this creature, see if they want to do anything special.
		boolean didSpecial = CreatureLogic.didTakeSpecialActions(context, entityCollection, mutable);
		if (didSpecial)
		{
			EntityActionSimpleMove<IMutableCreatureEntity> change = _createStandingChange(context, mutable);
			boolean didApply = change.applyChange(context, mutable);
			// We just asked to create this so failure doesn't make sense.
			Assert.assertTrue(didApply);
		}
		else
		{
			// If we didn't perform a special action, we can proceed with movement.
			_runInternalChanges(context, mutable, context.millisPerTick);
		}
		
		// Perform our usual "end of tick" concerns.
		// Apply fall damage.
		byte fallDamage = TickUtils.calculateFallDamage(startZVelocity - mutable.newVelocity.z());
		if (fallDamage > 0)
		{
			DamageHelpers.applyDamageDirectlyAndPostEvent(context, mutable, (byte)fallDamage, EventRecord.Cause.FALL);
		}
		// See if we need to "nudge" anyone this tick.
		NudgeHelpers.nudgeAsCreature(Environment.getShared(), context, entityCollection, creature);
		// Perform common end of tick processing.
		TickUtils.endOfTick(context, mutable);
		
		// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
		// This freeze() call will return the original instance if it is identical.
		// Note that the creature will become null if it died.
		CreatureEntity newEntity = mutable.freeze();
		return new SingleCreatureResult(newEntity, didSpecial);
	}

	private static void _runExternalChanges(TickProcessingContext context
		, MutableCreature mutable
		, List<IEntityAction<IMutableCreatureEntity>> changes
	)
	{
		for (IEntityAction<IMutableCreatureEntity> change : changes)
		{
			// Note that we ignore this response since it can fail.
			change.applyChange(context, mutable);
		}
	}

	private static void _runInternalChanges(TickProcessingContext context
		, MutableCreature mutable
		, long millisAtEndOfTick
	)
	{
		// Note that this may still return a null list of next steps if there is nothing to do.
		EntityActionSimpleMove<IMutableCreatureEntity> change = CreatureLogic.planNextAction(context, mutable, millisAtEndOfTick);
		if (null == change)
		{
			// In this case, we just want to synthesize a "do nothing" standing action so that we fall, etc.
			change = _createStandingChange(context, mutable);
		}
		boolean didApply = change.applyChange(context, mutable);
		// We just asked to create this so failure doesn't make sense.
		Assert.assertTrue(didApply);
	}

	private static EntityActionSimpleMove<IMutableCreatureEntity> _createStandingChange(TickProcessingContext context
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

	public static record SingleCreatureResult(CreatureEntity updatedEntity
		, boolean didTakeSpecialAction
	) {}
}
