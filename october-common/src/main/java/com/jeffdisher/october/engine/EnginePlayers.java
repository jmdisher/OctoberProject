package com.jeffdisher.october.engine;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Static engine logic related to processing player entities in the world.
 */
public class EnginePlayers
{
	/**
	 * The ID used for the operator "no entity" cases (that is, when the operator runs an action but not against any
	 * specific entity).
	 */
	public static final int OPERATOR_ENTITY_ID = Integer.MIN_VALUE;


	private EnginePlayers()
	{
		// This is just static logic.
	}

	/**
	 * Processes the given list of operator actions.  Note that operator actions work like normal player actions but
	 * they are applied to a null entity.  These originate from the console.
	 * 
	 * @param context The current tick context.
	 * @param operatorChanges The list of operator actions (not null).
	 */
	public static void processOperatorActions(TickProcessingContext context
		, List<IEntityAction<IMutablePlayerEntity>> operatorChanges
	)
	{
		for (IEntityAction<IMutablePlayerEntity> change : operatorChanges)
		{
			change.applyChange(context, null);
		}
	}

	/**
	 * Applies a list of actions to the given player entity.  Note that only the actions which are currently "ready" are
	 * run, with those which aren't yet ready updated and returned as part of the output.
	 * 
	 * @param context The current tick context.
	 * @param entityCollection The entities in the world.
	 * @param entity The player entity.
	 * @param changes The list of actions to apply (or update for a future tick).
	 * @return The results of the run (returned entity will be null if unchanged).
	 */
	public static SinglePlayerResult processOnePlayer(TickProcessingContext context
		, EntityCollection entityCollection
		, Entity entity
		, List<ScheduledChange> changes
	)
	{
		int entityChangesProcessed = 0;
		int committedMutationCount = 0;
		MutableEntity mutable = MutableEntity.existing(entity);
		float startZVelocity = mutable.newVelocity.z();
		List<ScheduledChange> notYetReadyChanges = new ArrayList<>();
		for (ScheduledChange scheduled : changes)
		{
			long millisUntilReady = scheduled.millisUntilReady();
			IEntityAction<IMutablePlayerEntity> change = scheduled.change();
			if (0L == millisUntilReady)
			{
				entityChangesProcessed += 1;
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
		
		// Perform our usual "end of tick" concerns.
		// Apply fall damage.
		byte fallDamage = TickUtils.calculateFallDamage(startZVelocity - mutable.newVelocity.z());
		if (fallDamage > 0)
		{
			DamageHelpers.applyDamageDirectlyAndPostEvent(context, mutable, (byte)fallDamage, EventRecord.Cause.FALL);
		}
		// See if we need to "nudge" anyone this tick.
		NudgeHelpers.nudgeAsPlayer(Environment.getShared(), context, entityCollection, entity);
		// Perform common end of tick processing.
		TickUtils.endOfTick(context, mutable);
		
		// If there was a change, we want to send it back so that the snapshot can be updated and clients can be informed.
		// This freeze() call will return the original instance if it is identical.
		Entity newEntity = mutable.freeze();
		Entity entityToReturn = (newEntity != entity)
			? newEntity
			: null
		;
		return new SinglePlayerResult(entityToReturn
			, notYetReadyChanges
			, entityChangesProcessed
			, committedMutationCount
		);
	}


	public static record SinglePlayerResult(Entity changedEntityOrNull
		, List<ScheduledChange> notYetReadyChanges
		, int entityChangesProcessed
		, int committedMutationCount
	) {}
}
