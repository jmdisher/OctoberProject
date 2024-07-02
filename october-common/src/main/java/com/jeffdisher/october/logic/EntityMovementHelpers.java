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
	 * Updates the given newEntity location to account for direct movement, as well as passive z-vector changes.
	 * Updates newEntity's location, velocity, and energy usage.
	 * 
	 * @param context The context.
	 * @param newEntity The entity to update.
	 * @param longMillisInMotion How many milliseconds of motion to consider.
	 * @param xDistance The distance the entity should move in the x-direction.
	 * @param yDistance The distance the entity should move in the y-direction.
	 * @return True if any motion change was applied, false if nothing changed or could change.
	 */
	public static boolean moveEntity(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
			, float xDistance
			, float yDistance
	)
	{
		return _handleMotion(context, newEntity, longMillisInMotion, xDistance, yDistance);
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
		return _handleMotion(context, newEntity, longMillisInMotion, 0.0f, 0.0f);
	}


	private static boolean _handleMotion(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
			, float xDistance
			, float yDistance
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
			newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), newZVector));
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
					newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), newZVector));
					didMove = true;
				}
				else
				{
					// We can't find a ceiling we can fit under so we are probably hitting a wall.
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
					newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), newZVector));
					didMove = true;
				}
				else
				{
					// We can't find a floor we can land on so we are probably hitting a wall.
					didMove = false;
				}
			}
			else
			{
				// We just hit a wall.
				didMove = false;
			}
		}
		
		// If we ended up moving, see how much energy was expended.
		if (didMove)
		{
			// We only account for the x/y movement (we assume we paid for jumping and falling is free).
			// We also just sum these, instead of taking the diagonal, just for simplicity (most of the rest of the system also ignores diagonals).
			EntityLocation finalLocation = newEntity.getLocation();
			float distance = Math.abs(finalLocation.x() - oldLocation.x()) + Math.abs(finalLocation.y() - oldLocation.y());
			int cost = (int)(distance * (float)EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK);
			newEntity.applyEnergyCost(context, cost);
		}
		return didMove;
	}
}
