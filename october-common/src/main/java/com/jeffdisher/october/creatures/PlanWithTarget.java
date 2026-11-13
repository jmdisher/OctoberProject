package com.jeffdisher.october.creatures;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is to move to a specific location, via a complex path, in pursuit of an entity.
 */
public class PlanWithTarget extends CreatureEntity.MovementPlan
{
	public PlanWithTarget(List<PathLocation> path
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		super(path
			, targetEntityId
			, targetPreviousLocation
			, null
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertTrue(!path.isEmpty());
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
	}
}
