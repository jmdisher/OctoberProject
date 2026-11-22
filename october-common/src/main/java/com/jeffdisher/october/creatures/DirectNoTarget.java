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
 * When the plan is just a location which can be directly approached on this Z-level.
 */
public class DirectNoTarget implements CreatureEntity.IMovementPlan
{
	private final EntityLocation _directLocation;
	// The intensity of movement to apply if there is any.
	private final EntityActionSimpleMove.Intensity _intensity;

	public DirectNoTarget(EntityLocation directLocation
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		Assert.assertTrue(null != directLocation);
		Assert.assertTrue(null != intensity);
		
		_directLocation = directLocation;
		_intensity = intensity;
	}

	@Override
	public int targetEntityId()
	{
		return CreatureEntity.NO_TARGET_ENTITY_ID;
	}

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		EntityActionSimpleMove<MutableCreature> action = CreatureMovementHelpers.moveAlongDiagonalPath(context.previousBlockLookUp
			, currentLocation
			, type
			, _directLocation
			, timeLimitMillis
			, _intensity
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
		EntityLocation currentLocation = mutable.getLocation();
		
		// Handle the case where we are no longer on the same Z-level as our direct target.
		if (0 != PathLocation.adjacentStepsZ(PathLocation.forEntityBase(currentLocation), PathLocation.forEntityBase(_directLocation)))
		{
			mutable.movementPlan = null;
		}
		
		if (mutable.movementPlan instanceof DirectNoTarget)
		{
			// Check if the plan should be updated due to proximity to the target.
			if (currentLocation.equals(_directLocation))
			{
				// We are at our destination, so clear.
				mutable.movementPlan = null;
			}
			else
			{
				// We are still walking there, so leave this unchanged.
			}
		}
	}

	public EntityLocation test_getDirectLocation()
	{
		return _directLocation;
	}

	public EntityActionSimpleMove.Intensity test_getIntensity()
	{
		return _intensity;
	}
}
