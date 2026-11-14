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

	@Override
	public EntityActionSimpleMove<MutableCreature> planNextAction(TickProcessingContext context
		, MutableCreature mutable
		, long timeLimitMillis
	)
	{
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
			, this.intensity
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
