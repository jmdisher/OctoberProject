package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is to directly approach a target entity on this Z-level.
 * In this case, we are assuming that target's location can be our destination.
 */
public class DirectWithTarget implements CreatureEntity.IMovementPlan
{
	// The ID of the entity this creature is currently targeting.
	private final int _targetEntityId;
	// The last location of the target which was used to determine the movementPlan
	private final EntityLocation _targetPreviousLocation;

	public DirectWithTarget(int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
		
		_targetEntityId = targetEntityId;
		_targetPreviousLocation = targetPreviousLocation;
	}

	@Override
	public int targetEntityId()
	{
		return _targetEntityId;
	}

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
		// NOTE:  In this implementation, we always assume that we are walking directly toward the target.
		EntityLocation directLocation = _targetPreviousLocation;
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity .WALKING;
		
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		EntityActionSimpleMove<MutableCreature> action = CreatureMovementHelpers.moveAlongDiagonalPath(context.previousBlockLookUp
			, currentLocation
			, type
			, directLocation
			, timeLimitMillis
			, intensity
		);
		
		if (null == action)
		{
			// This only returns null if we are stuck so clear the plan.
			// Ideally, we would clear this in the "special action" phase but this is only detected here.
			mutable.movementPlan = null;
		}
		return action;
	}

	@Override
	public void updateMovementPlan(TickProcessingContext context
		, EntityCollection entityCollection
		, MutableCreature mutable
	)
	{
		// Check if the plan should be update in response to a target having moved.
		CreatureMovementHelpers.updateTargetEntityPlan(context, mutable, _targetEntityId, _targetPreviousLocation);
		
		if (mutable.movementPlan instanceof DirectWithTarget)
		{
			// Handle the case where we are no longer on the same Z-level as our direct target.
			// NOTE:  In this implementation, we always assume that we are walking directly toward the target.
			EntityLocation directLocation = _targetPreviousLocation;
			if (mutable.newLocation.getBlockLocation().z() != directLocation.getBlockLocation().z())
			{
				mutable.movementPlan = null;
			}
		}
		
		if (mutable.movementPlan instanceof DirectWithTarget)
		{
			// Check if the plan should be updated due to proximity to the target.
			CreatureMovementHelpers.updateDirectTargetPlan(context, mutable, _targetEntityId);
		}
	}

	public EntityLocation test_getTargetPreviousLocation()
	{
		return _targetPreviousLocation;
	}
}
