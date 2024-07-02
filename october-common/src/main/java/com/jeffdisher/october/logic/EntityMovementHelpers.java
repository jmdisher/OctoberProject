package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helper class containing the common logic around entity location/velocity and how they are updated with time and
 * validated against the impassable blocks in the environment.
 */
public class EntityMovementHelpers
{
	/**
	 * Sets the velocity vector of the given newEntity to account for the velocity required to cross the given distance
	 * in the allotted number of millisInMotion.  No internal validation is done to verify that this is an appropriate
	 * velocity for this creation.
	 * This helper will also apply the movement energy cost to the entity.
	 * 
	 * @param context The context.
	 * @param newEntity The entity to update.
	 * @param longMillisInMotion How many milliseconds of motion to consider.
	 * @param xDistance The distance the entity should move in the x-direction.
	 * @param yDistance The distance the entity should move in the y-direction.
	 */
	public static void setVelocity(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long millisInMotion
			, float xDistance
			, float yDistance
	)
	{
		// First set the velocity.
		float secondsInMotion = ((float)millisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		EntityLocation oldVector = newEntity.getVelocityVector();
		float newX = xDistance / secondsInMotion;
		float newY = yDistance / secondsInMotion;
		newEntity.setVelocityVector(new EntityLocation(newX, newY, oldVector.z()));
		
		// Then pay for the acceleration.
		// We only account for the x/y movement (we assume we paid for jumping and falling is free).
		// We also just sum these, instead of taking the diagonal, just for simplicity (most of the rest of the system also ignores diagonals).
		float distance = Math.abs(xDistance) + Math.abs(yDistance);
		int cost = (int)(distance * (float)EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK);
		newEntity.applyEnergyCost(context, cost);
	}

	/**
	 * Updates the given newEntity location to account for passive rising/falling motion, and updates the z-factor.
	 * Updates newEntity's location, velocity, and energy usage.
	 * 
	 * @param context The context.
	 * @param newEntity The entity to update.
	 * @param longMillisInMotion How many milliseconds of motion to consider.
	 * @return True if any motion change was applied, false if nothing changed or could change.
	 */
	public static boolean allowMovement(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
	)
	{
		return _handleMotion(context, newEntity, longMillisInMotion);
	}


	private static boolean _handleMotion(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
	)
	{
		// First of all, we need to figure out if we should be changing our z-vector:
		// -cancel positive vector if we hit the ceiling
		// -cancel negative vector if we hit the ground
		// -apply gravity in any other case
		EntityLocation vector = newEntity.getVelocityVector();
		float initialZVector = vector.z();
		EntityLocation oldLocation = newEntity.getLocation();
		EntityVolume volume = EntityConstants.getVolume(newEntity.getType());
		float secondsInMotion = ((float)longMillisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float newZVector;
		boolean shouldAllowFalling;
		if ((initialZVector > 0.0f) && SpatialHelpers.isTouchingCeiling(context.previousBlockLookUp, oldLocation, volume))
		{
			// We are up against the ceiling so cancel the velocity.
			newZVector = 0.0f;
			shouldAllowFalling = true;
		}
		else if ((initialZVector <= 0.0f) && SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, oldLocation, volume))
		{
			// We are on the ground so cancel the velocity.
			newZVector = 0.0f;
			shouldAllowFalling = false;
		}
		else
		{
			newZVector = MotionHelpers.applyZAcceleration(initialZVector, secondsInMotion);
			shouldAllowFalling = true;
		}
		
		// We need to decide how far they would move in the x or y directions based on the current velocity and time.
		// (for now, we will just apply the movement based on the current velocity, not accounting for friction-based deceleration).
		float xDistance = secondsInMotion * vector.x();
		float yDistance = secondsInMotion * vector.y();
		// Figure out where our new location is (requires calculating the z-movement in this time).
		float zDistance = shouldAllowFalling
				? MotionHelpers.applyZMovement(initialZVector, secondsInMotion)
				: 0.0f
		;
		float oldZ = oldLocation.z();
		float xLocation = oldLocation.x() + xDistance;
		float yLocation = oldLocation.y() + yDistance;
		float zLocation = oldZ + zDistance;
		EntityLocation newLocation = new EntityLocation(xLocation, yLocation, zLocation);
		
		boolean didMove;
		if ((initialZVector == newZVector) && oldLocation.equals(newLocation))
		{
			// We don't want to apply this change if the entity isn't actually moving, since that will cause redundant update events.
			didMove = false;
		}
		else if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, newLocation, volume))
		{
			// They can exist in the target location so update the entity.
			newEntity.setLocation(newLocation);
			newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, newZVector));
			didMove = true;
		}
		else
		{
			// This can happen when we bump into a wall (which would be a failure) but we will handle the special case of hitting the floor or ceiling and clamp the z.
			if (zDistance > 0.0f)
			{
				// We were jumping to see if we can clamp our location under the block.
				EntityLocation highestLocation = SpatialHelpers.locationTouchingCeiling(context.previousBlockLookUp, newLocation, volume, oldZ);
				if (null != highestLocation)
				{
					newEntity.setLocation(highestLocation);
					newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, newZVector));
					didMove = true;
				}
				else
				{
					// We can't find a ceiling we can fit under so we are probably hitting a wall.
					newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, vector.z()));
					didMove = false;
				}
			}
			else if (zDistance < 0.0f)
			{
				// We were falling so see if we can stop on the block(s) above where we fell.
				EntityLocation lowestLocation = SpatialHelpers.locationTouchingGround(context.previousBlockLookUp, newLocation, volume, oldZ);
				if (null != lowestLocation)
				{
					newEntity.setLocation(lowestLocation);
					newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, newZVector));
					didMove = true;
				}
				else
				{
					// We can't find a floor we can land on so we are probably hitting a wall.
					newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, vector.z()));
					didMove = false;
				}
			}
			else
			{
				// We just hit a wall.
				newEntity.setVelocityVector(new EntityLocation(0.0f, 0.0f, vector.z()));
				didMove = false;
			}
		}
		return didMove;
	}
}
