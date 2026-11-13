package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Similar to DirectWithTarget but this is when there are multiple steps to reach the entity so we can't walk directly
 * toward it but we do have some direct path to clear before finding a more complex path.
 */
public class DirectWithDistantTarget extends CreatureEntity.MovementPlan
{
	public DirectWithDistantTarget(EntityLocation directLocation
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		super(null
			, targetEntityId
			, targetPreviousLocation
			, directLocation
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertTrue(null != directLocation);
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
	}
}
