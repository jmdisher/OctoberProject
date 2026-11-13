package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is just a location which can be directly approached on this Z-level.
 */
public class DirectNoTarget extends CreatureEntity.MovementPlan
{
	public DirectNoTarget(EntityLocation directLocation
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		super(null
			, CreatureEntity.NO_TARGET_ENTITY_ID
			, null
			, directLocation
			, intensity
		);
		Assert.assertTrue(null != directLocation);
		Assert.assertTrue(null != intensity);
	}
}
