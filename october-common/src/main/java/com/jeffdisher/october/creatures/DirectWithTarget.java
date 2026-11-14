package com.jeffdisher.october.creatures;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
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

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
		// NOTE:  In this implementation, we always assume that we are walking directly toward the target.
		EntityLocation directLocation = this.targetPreviousLocation;
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity .WALKING;
		
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
		
		byte yaw = OrientationHelpers.getYawBetweenPoints(currentLocation, directLocation);
		EntityActionSimpleMove<MutableCreature> action = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, currentLocation
			, yaw
			, (byte)0
			, type
			, directLocation
			, timeLimitMillis
			, viscosity
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
}
