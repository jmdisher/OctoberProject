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

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity .WALKING;
		
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
		
		byte yaw = OrientationHelpers.getYawBetweenPoints(currentLocation, this.directLocation);
		EntityActionSimpleMove<MutableCreature> action = CreatureMovementHelpers.moveAlongDiagonalPath(reader
			, currentLocation
			, yaw
			, (byte)0
			, type
			, this.directLocation
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
