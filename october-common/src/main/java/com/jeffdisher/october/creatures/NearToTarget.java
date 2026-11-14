package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Similar to DirectWithTarget but used in the case where the target is so close that no movement should be made.
 * This case will likely be replaced with DirectWithTarget once the logic to make a move is updated to avoid movement
 * when within range.
 */
public class NearToTarget extends CreatureEntity.MovementPlan
{
	public NearToTarget(int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		super(null
			, targetEntityId
			, targetPreviousLocation
			, null
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
	}

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
		// In this implementation, we are already close enough so we don't move.
		return null;
	}
}
