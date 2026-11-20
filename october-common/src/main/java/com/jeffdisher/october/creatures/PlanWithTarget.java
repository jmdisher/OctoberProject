package com.jeffdisher.october.creatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
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
 * When the plan is to move to a specific location, via a complex path, in pursuit of an entity.
 */
public class PlanWithTarget implements CreatureEntity.IMovementPlan
{
	// The full plan, starting with the next step the creature must enter.  Can be null but never empty.
	private final List<PathLocation> _fullPlan;
	// The ID of the entity this creature is currently targeting (or NO_TARGET_ENTITY_ID if none).
	private final int _targetEntityId;
	// The last location of the target which was used to determine the movementPlan (can be null if we have no
	// target or aren't tracking the current one).
	private final EntityLocation _targetPreviousLocation;

	public PlanWithTarget(List<PathLocation> path
		, int targetEntityId
		, EntityLocation targetPreviousLocation
	)
	{
		Assert.assertTrue(!path.isEmpty());
		Assert.assertTrue(CreatureEntity.NO_TARGET_ENTITY_ID != targetEntityId);
		Assert.assertTrue(null != targetPreviousLocation);
		
		_fullPlan = path;
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
		EntityType type = mutable.getType();
		EntityLocation currentLocation = mutable.getLocation();
		EntityLocation oldVelocity = mutable.getVelocityVector();
		
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity .WALKING;
		
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
		EntityActionSimpleMove<MutableCreature> actionProduced = CreatureMovementHelpers.prepareForMove(context.previousBlockLookUp
			, currentLocation
			, oldVelocity
			, type
			, direction
			, timeLimitMillis
			, intensity
		);
		if (null == actionProduced)
		{
			// If we are already in a reasonable location, proceed to move.
			// We know that this should only be called AFTER updating our planned path.
			Assert.assertTrue(!PathLocation.forEntityBase(currentLocation).equals(first));
			
			actionProduced = CreatureMovementHelpers.moveToNextLocation(context.previousBlockLookUp
				, currentLocation
				, oldVelocity
				, type
				, first
				, second
				, timeLimitMillis
				, intensity
			);
		}
		// TODO:  This approach is a hack which requires that we rethink this action production design.
		if ((null != actionProduced) && (actionProduced.getSubAction() instanceof EntitySubActionStepUp))
		{
			List<PathLocation> fullPlan = new ArrayList<>(_fullPlan);
			fullPlan.remove(0);
			mutable.movementPlan = new PlanWithTarget(fullPlan
				, _targetEntityId
				, _targetPreviousLocation
			);
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
		
		if (mutable.movementPlan instanceof PlanWithTarget)
		{
			// Check if the plan should be update in response to a target having moved.
			CreatureMovementHelpers.updateTargetEntityPlan(context, mutable, _targetEntityId, _targetPreviousLocation);
		}
		
		if (mutable.movementPlan instanceof PlanWithTarget)
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
					
					ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
					EntityLocation directLocation = CreatureMovementHelpers.findDirectTarget(reader, currentLocation, type, updatedPlan);
					
					CreatureEntity.IMovementPlan movementPlan;
					if (null != directLocation)
					{
						movementPlan = new DirectWithDistantTarget(directLocation, _targetEntityId, _targetPreviousLocation);
					}
					else
					{
						movementPlan = new PlanWithTarget(Collections.unmodifiableList(updatedPlan), _targetEntityId, _targetPreviousLocation);
					}
					mutable.movementPlan = movementPlan;
				}
				else
				{
					// We assume that they are in range as a near target.
					mutable.movementPlan = new DirectWithTarget(_targetEntityId, _targetPreviousLocation);
				}
			}
		}
	}

	public List<PathLocation> test_getFullPlan()
	{
		return _fullPlan;
	}

	public EntityLocation test_getTargetPreviousLocation()
	{
		return _targetPreviousLocation;
	}
}
