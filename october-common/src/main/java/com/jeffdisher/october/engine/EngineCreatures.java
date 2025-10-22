package com.jeffdisher.october.engine;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.creatures.CreatureLogic;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.NudgeHelpers;
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
	 * Runs all elements in changesToRun against the given creature, returning a description of the change.
	 * 
	 * @param context The context used for running changes.
	 * @param entityCollection A look-up mechanism for the entities in the loaded world.
	 * @param creature The creature to process.
	 * @param changesToRun A list of changes to run on this creature in this tick.
	 * @return A description of the results of processing this creature.
	 */
	public static SingleCreatureResult processOneCreature(TickProcessingContext context
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
		if (TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			TickUtils.applyEnvironmentalDamage(context, mutable);
		}
		
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
		return new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, mutable.newYaw
			, mutable.newPitch
			, null
		);
	}


	public static record SingleCreatureResult(CreatureEntity updatedEntity
		, boolean didTakeSpecialAction
	) {}
}
