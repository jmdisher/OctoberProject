package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.pathing.PathLocation;
import com.jeffdisher.october.subactions.EntitySubActionStepUp;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * When the plan is to move to a specific location, via a complex path, in pursuit of an entity.
 */
public class PlanWithTarget extends CreatureEntity.MovementPlan
{
	public PlanWithTarget(List<PathLocation> path
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		super(path
			, targetEntityId
			, targetPreviousLocation
			, null
			, EntityActionSimpleMove.Intensity.WALKING
		);
		Assert.assertTrue(!path.isEmpty());
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
		
		EntityLocation oldVelocity = mutable.getVelocityVector();
		
		float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
		
		// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
		PathLocation currentInPath = PathLocation.forEntityBase(currentLocation);
		PathLocation first = this.fullPlan.get(0);
		PathLocation second = (this.fullPlan.size() >= 2) ? this.fullPlan.get(1) : null;
		FacingDirection direction;
		if ((this.fullPlan.size() > 2)
			&& (FacingDirection.UP == PathLocation.adjacentDirection(currentInPath, first))
			&& (FacingDirection.UP == PathLocation.adjacentDirection(first, this.fullPlan.get(1)))
		)
		{
			// This means we are jumping so choose the next place where we want to go for direction hint.
			PathLocation third = this.fullPlan.get(2);
			direction = PathLocation.adjacentDirection(second, third);
		}
		else
		{
			// Otherwise, we just want to lean into the direction we are currently heading.
			direction = PathLocation.adjacentDirection(currentInPath, first);
		}
		EntityActionSimpleMove<MutableCreature> actionProduced = CreatureMovementHelpers.prepareForMove(currentLocation
			, oldVelocity
			, type
			, direction
			, timeLimitMillis
			, viscosity
			, intensity
		);
		if (null == actionProduced)
		{
			// If we are already in a reasonable location, proceed to move.
			// We know that this should only be called AFTER updating our planned path.
			Assert.assertTrue(!PathLocation.forEntityBase(currentLocation).equals(first));
			
			boolean isSwimmable = (viscosity >= BlockAspect.VISOSITY_FLOAT_SWIMMABLE) && (viscosity < BlockAspect.VISOSITY_FLOAT_SOLID);
			actionProduced = CreatureMovementHelpers.moveToNextLocation(reader
				, currentLocation
				, oldVelocity
				, mutable.newYaw
				, mutable.newPitch
				, type
				, first
				, second
				, timeLimitMillis
				, viscosity
				, intensity
				, isSwimmable
			);
		}
		// TODO:  This approach is a hack which requires that we rethink this action production design.
		if ((null != actionProduced) && (actionProduced.getSubAction() instanceof EntitySubActionStepUp))
		{
			List<PathLocation> fullPlan = new ArrayList<>(this.fullPlan);
			fullPlan.remove(0);
			mutable.movementPlan = new PlanWithTarget(fullPlan
				, this.targetEntityId
				, this.targetPreviousLocation
			);
		}
		return actionProduced;
	}
}
