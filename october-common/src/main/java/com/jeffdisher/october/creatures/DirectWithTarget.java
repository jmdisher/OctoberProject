package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is to directly approach a target entity on this Z-level.
 * In this case, we are assuming that target's location can be our destination.
 */
public class DirectWithTarget extends CreatureEntity.MovementPlan
{
	public DirectWithTarget(int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		super(null
			, targetEntityId
			, targetPreviousLocation
			, targetPreviousLocation
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
	}
}
