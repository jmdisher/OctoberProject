package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.logic.EntityCollection;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Similar to DirectWithTarget but this is when there are multiple steps to reach the entity so we can't walk directly
 * toward it but we do have some direct path to clear before finding a more complex path.
 */
public class DirectWithDistantTarget implements CreatureEntity.IMovementPlan
{
	// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
	private final int _targetEntityId;
	// The last location of the target which was used to determine the movementPlan (can be null if we have no
	// target or aren't tracking the current one).
	private final EntityLocation _targetPreviousLocation;
	// In the cases where we need a very short walk, or where we are going to take a short-cut through fullPlan, we
	// use this location.
	private final EntityLocation _directLocation;

	public DirectWithDistantTarget(EntityLocation directLocation
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		Assert.assertTrue(null != directLocation);
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
		
		_targetEntityId = targetEntityId;
		_targetPreviousLocation = targetPreviousLocation;
		_directLocation = directLocation;
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
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity .WALKING;
		
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		EntityActionSimpleMove<MutableCreature> action = CreatureMovementHelpers.moveAlongDiagonalPath(context.previousBlockLookUp
			, currentLocation
			, type
			, _directLocation
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
		
		if (mutable.movementPlan instanceof DirectWithDistantTarget)
		{
			// Handle the case where we are no longer on the same Z-level as our direct target.
			if (0 != PathLocation.adjacentStepsZ(PathLocation.forEntityBase(mutable.newLocation), PathLocation.forEntityBase(_directLocation)))
			{
				mutable.movementPlan = null;
			}
		}
		
		if (mutable.movementPlan instanceof DirectWithDistantTarget)
		{
			// Check if the plan should be updated due to proximity to the target.
			CreatureMovementHelpers.updateDirectTargetPlan(context, mutable, _targetEntityId);
		}
	}

	public EntityLocation test_getTargetPreviousLocation()
	{
		return _targetPreviousLocation;
	}

	public EntityLocation test_getDirectLocation()
	{
		return _directLocation;
	}
}
