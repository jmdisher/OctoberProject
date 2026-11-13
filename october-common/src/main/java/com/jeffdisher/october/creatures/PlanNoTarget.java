package com.jeffdisher.october.creatures;

import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is to move to a specific location, via a complex path, but not in pursuit of an entity.
 */
public class PlanNoTarget extends CreatureEntity.MovementPlan
{
	public PlanNoTarget(List<PathLocation> path
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		super(path
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, null
			, intensity
		);
		Assert.assertTrue(!path.isEmpty());
		Assert.assertTrue(null != intensity);
	}
}
