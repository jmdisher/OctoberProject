package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.logic.EntityCollection;
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
public class NearToTarget implements CreatureEntity.IMovementPlan
{
	// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
	private final int _targetEntityId;
	// The last location of the target which was used to determine the movementPlan (can be null if we have no
	// target or aren't tracking the current one).
	private final EntityLocation _targetPreviousLocation;

	public NearToTarget(int targetEntityId
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
		// In this implementation, we are already close enough so we don't move.
		return null;
	}

	@Override
	public void updateMovementPlan(TickProcessingContext context
		, EntityCollection entityCollection
		, MutableCreature mutable
	)
	{
		// Check if the plan should be update in response to a target having moved.
		CreatureMovementHelpers.updateTargetEntityPlan(context, mutable, _targetEntityId, _targetPreviousLocation);
	}

	public EntityLocation test_getTargetPreviousLocation()
	{
		return _targetPreviousLocation;
	}
}
