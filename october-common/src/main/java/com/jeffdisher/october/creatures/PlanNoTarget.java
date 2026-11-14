package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionSimpleMove.Intensity;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityCollection;
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
 * When the plan is to move to a specific location, via a complex path, but not in pursuit of an entity.
 */
public class PlanNoTarget implements CreatureEntity.IMovementPlan
{
	// The full plan, starting with the next step the creature must enter.  Can be null but never empty.
	private final List<PathLocation> _fullPlan;
	// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
	// The intensity of movement to apply if there is any.
	private final EntityActionSimpleMove.Intensity _intensity;

	public PlanNoTarget(List<PathLocation> path
		, EntityActionSimpleMove.Intensity intensity
	)
	{
		Assert.assertTrue(!path.isEmpty());
		Assert.assertTrue(null != intensity);
		
		_fullPlan = path;
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
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		
		EntityLocation oldVelocity = mutable.getVelocityVector();
		
		float viscosity = reader.getMaxStillViscosityInVolume(currentLocation, type.volume());
		
		// We have a path so make sure that we start in a reasonable part of the block so we don't bump into something or fail to jump out of a hole.
		PathLocation currentInPath = PathLocation.forEntityBase(currentLocation);
		PathLocation first = _fullPlan.get(0);
		PathLocation second = (_fullPlan.size() >= 2) ? _fullPlan.get(1) : null;
		FacingDirection direction;
		if ((_fullPlan.size() > 2)
			&& (FacingDirection.UP == PathLocation.adjacentDirection(currentInPath, first))
			&& (FacingDirection.UP == PathLocation.adjacentDirection(first, _fullPlan.get(1)))
		)
		{
			// This means we are jumping so choose the next place where we want to go for direction hint.
			PathLocation third = _fullPlan.get(2);
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
			, _intensity
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
				, _intensity
				, isSwimmable
			);
		}
		// TODO:  This approach is a hack which requires that we rethink this action production design.
		if ((null != actionProduced) && (actionProduced.getSubAction() instanceof EntitySubActionStepUp))
		{
			List<PathLocation> fullPlan = new ArrayList<>(_fullPlan);
			fullPlan.remove(0);
			mutable.movementPlan = new PlanNoTarget(fullPlan, _intensity);
		}
		return actionProduced;
	}

	@Override
	public void updateMovementPlan(TickProcessingContext context
		, EntityCollection entityCollection
		, MutableCreature mutable
	)
	{
		CreatureMovementHelpers.validateNextStepValid(context, mutable, _fullPlan);
		
		if (mutable.movementPlan instanceof PlanNoTarget)
		{
			EntityLocation currentLocation = mutable.getLocation();
			
			PathLocation thisStep = _fullPlan.get(0);
			PathLocation pathBase = PathLocation.forEntityBase(currentLocation);
			
			if (pathBase.equals(thisStep))
			{
				// We are in the next part of the plan so remove this step.
				if (_fullPlan.size() > 1)
				{
					EntityType type = mutable.getType();
					
					// Remove this one element and stay in the same plan.
					List<PathLocation> updatedPlan = new ArrayList<>(_fullPlan);
					updatedPlan.remove(0);
					
					List<PathLocation> steps = Collections.unmodifiableList(updatedPlan);
					Intensity intensity = EntityActionSimpleMove.Intensity.IDLE_CREATURE;
					ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
					EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, steps);
					
					CreatureEntity.IMovementPlan movementPlan;
					if (null != directLocation)
					{
						movementPlan = new DirectNoTarget(directLocation, intensity);
					}
					else
					{
						movementPlan = new PlanNoTarget(steps, intensity);
					}
					mutable.movementPlan = movementPlan;
				}
				else
				{
					// This means clear the plan since we must have been idling.
					mutable.movementPlan = null;
				}
			}
		}
	}

	public List<PathLocation> test_getFullPlan()
	{
		return _fullPlan;
	}

	public EntityActionSimpleMove.Intensity test_getIntensity()
	{
		return _intensity;
	}
}
