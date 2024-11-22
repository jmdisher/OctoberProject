package com.jeffdisher.october.logic;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;


/**
 * Helper class containing the common logic around entity location/velocity and how they are updated with time and
 * validated against the impassable blocks in the environment.
 */
public class EntityMovementHelpers
{
	/**
	 * Sets the velocity of the given newEntity based on the given x/y components and a target total speed of
	 * blocksPerSecond.
	 * Note:  No check is done on xComponent or yComponent but they should typically describe a combined vector of 1.0f
	 * length.
	 * This helper will also apply the movement energy cost to the entity.
	 * 
	 * @param newEntity The entity to update.
	 * @param blocksPerSecond The speed of the entity for this time.
	 * @param millisInMotion How many milliseconds of motion to consider.
	 * @param xComponent The x-component of the motion (note that sqrt(x^2 + y ^2) should probably be <= 1.0).
	 * @param yComponent The y-component of the motion (note that sqrt(x^2 + y ^2) should probably be <= 1.0).
	 */
	public static void accelerate(IMutableMinimalEntity newEntity
			, float blocksPerSecond
			, long millisInMotion
			, float xComponent
			, float yComponent
	)
	{
		// First set the velocity.
		float secondsInMotion = ((float)millisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		EntityLocation oldVector = newEntity.getVelocityVector();
		float targetBlocksInStep = (blocksPerSecond * secondsInMotion);
		float newX = xComponent * blocksPerSecond;
		float newY = yComponent * blocksPerSecond;
		newEntity.setVelocityVector(new EntityLocation(newX, newY, oldVector.z()));
		
		// Then pay for the acceleration.
		int cost = (int)(targetBlocksInStep * (float)EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK);
		newEntity.applyEnergyCost(cost);
	}

	/**
	 * Updates the given newEntity location to account for passive rising/falling motion, and updates the z-factor.
	 * Updates newEntity's location, velocity, and energy usage.
	 * Note that this will reduce the horizontal velocity of newEntity to 0.0 (since we currently only have maximum
	 * friction on all blocks and also want to stop air drift immediately - not realistic but keeps this simple and
	 * makes the user feedback be what they would expect in a video game).
	 * 
	 * @param previousBlockLookUp Lookup function to find the blocks from the previous tick.
	 * @param newEntity The entity to update.
	 * @param longMillisInMotion How many milliseconds of motion to consider.
	 */
	public static void allowMovement(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
	)
	{
		Environment env = Environment.getShared();
		EntityLocation oldVector = newEntity.getVelocityVector();
		EntityLocation oldLocation = newEntity.getLocation();
		
		// First of all, we need to figure out if we should be changing our z-vector:
		// -apply viscosity to the previous vector (direct multiplier)
		// -cancel positive vector if we hit the ceiling
		// -cancel negative vector if we hit the ground
		// -apply gravity in any other case
		BlockProxy currentLocationProxy = previousBlockLookUp.apply(oldLocation.getBlockLocation());
		// We will apply viscosity as the material viscosity times the current velocity since that is simple and should appear reasonable.
		float viscosityFraction = (null != currentLocationProxy)
				? env.blocks.getViscosityFraction(currentLocationProxy.getBlock())
				: 1.0f
		;
		float initialZVector = oldVector.z();
		EntityVolume volume = EntityConstants.getVolume(newEntity.getType());
		float secondsInMotion = ((float)longMillisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float newZVector;
		boolean shouldAllowFalling;
		if ((initialZVector > 0.0f) && SpatialHelpers.isTouchingCeiling(previousBlockLookUp, oldLocation, volume))
		{
			// We are up against the ceiling so cancel the velocity.
			newZVector = 0.0f;
			shouldAllowFalling = true;
		}
		else if ((initialZVector <= 0.0f) && SpatialHelpers.isStandingOnGround(previousBlockLookUp, oldLocation, volume))
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
		
		// Note that we currently just set the x/y velocities to zero after applying the movement so just directly apply these through the viscosity.
		// TODO:  This assumption of setting x/y velocity to zero will need to change to support icy surfaces or "flying through the air".
		float velocityFraction = (1.0f - viscosityFraction);
		float velocityToApplyX = velocityFraction * oldVector.x();
		float velocityToApplyY = velocityFraction * oldVector.y();
		
		// We need to decide how far they would move in the x or y directions based on the current velocity and time.
		// (for now, we will just apply the movement based on the current velocity, not accounting for friction-based deceleration).
		float xDistance = secondsInMotion * velocityToApplyX;
		float yDistance = secondsInMotion * velocityToApplyY;
		// Figure out where our new location is (requires calculating the z-movement in this time).
		// Note that we directly fudge this by the velocity fraction from the viscosity.
		float rawZ = shouldAllowFalling
				? MotionHelpers.applyZMovement(initialZVector, secondsInMotion)
				: 0.0f
		;
		float zDistance = velocityFraction * rawZ;
		
		float oldX = oldLocation.x();
		float oldY = oldLocation.y();
		float oldZ = oldLocation.z();
		float xLocation = oldX + xDistance;
		float yLocation = oldY + yDistance;
		float zLocation = oldZ + zDistance;
		
		// We will incrementally search for barriers in each axis.
		// -X
		EntityLocation newLocation = new EntityLocation(xLocation, oldY, oldZ);
		if (!SpatialHelpers.canExistInLocation(previousBlockLookUp, newLocation, volume))
		{
			// Adjust the X axis.
			EntityLocation attempt = newLocation;
			if (xDistance > 0.0f)
			{
				EntityLocation adjustedLocation = SpatialHelpers.locationTouchingEastWall(previousBlockLookUp, newLocation, volume, oldX);
				if (null != adjustedLocation)
				{
					newLocation = adjustedLocation;
				}
			}
			else if (xDistance < 0.0f)
			{
				EntityLocation adjustedLocation = SpatialHelpers.locationTouchingWestWall(previousBlockLookUp, newLocation, volume, oldX);
				if (null != adjustedLocation)
				{
					newLocation = adjustedLocation;
				}
			}
			else
			{
				// We must be inside a wall.
			}
			
			if (attempt == newLocation)
			{
				// We can't stand anywhere so just cancel x movement.
				newLocation = new EntityLocation(oldX, newLocation.y(), newLocation.z());
			}
		}
		// The x-vector is always cancelled.
		float newXVector = 0.0f;
		
		// -Y
		newLocation = new EntityLocation(newLocation.x(), yLocation, newLocation.z());
		if (!SpatialHelpers.canExistInLocation(previousBlockLookUp, newLocation, volume))
		{
			// Adjust the Y axis.
			EntityLocation attempt = newLocation;
			if (yDistance > 0.0f)
			{
				EntityLocation adjustedLocation = SpatialHelpers.locationTouchingNorthWall(previousBlockLookUp, newLocation, volume, oldY);
				if (null != adjustedLocation)
				{
					newLocation = adjustedLocation;
				}
			}
			else if (yDistance < 0.0f)
			{
				EntityLocation adjustedLocation = SpatialHelpers.locationTouchingSouthWall(previousBlockLookUp, newLocation, volume, oldY);
				if (null != adjustedLocation)
				{
					newLocation = adjustedLocation;
				}
			}
			else
			{
				// We must be inside a wall.
			}
			
			if (attempt == newLocation)
			{
				// We can't stand anywhere so just cancel y movement.
				newLocation = new EntityLocation(newLocation.x(), oldY, newLocation.z());
			}
		}
		// The y-vector is always cancelled.
		float newYVector = 0.0f;
		
		// -Z
		newLocation = new EntityLocation(newLocation.x(), newLocation.y(), zLocation);
		if (!SpatialHelpers.canExistInLocation(previousBlockLookUp, newLocation, volume))
		{
			// Adjust the Z axis.
			EntityLocation attempt = newLocation;
			if (zDistance > 0.0f)
			{
				// We were jumping to see if we can clamp our location under the block.
				EntityLocation highestLocation = SpatialHelpers.locationTouchingCeiling(previousBlockLookUp, newLocation, volume, oldZ);
				if (null != highestLocation)
				{
					newLocation = highestLocation;
				}
			}
			else if (zDistance < 0.0f)
			{
				// We were falling so see if we can stop on the block(s) above where we fell.
				EntityLocation lowestLocation = SpatialHelpers.locationTouchingGround(previousBlockLookUp, newLocation, volume, oldZ);
				if (null != lowestLocation)
				{
					newLocation = lowestLocation;
				}
			}
			else
			{
				// We must be inside a wall.
			}
			
			if (attempt == newLocation)
			{
				// We can't stand anywhere so just cancel z movement.
				newLocation = new EntityLocation(newLocation.x(), newLocation.y(), oldZ);
			}
			
			// If for any reason we can't exist in this block, cancel the z-vector.
			newZVector = 0.0f;
		}
		
		// Set the location and velocity.
		newEntity.setLocation(newLocation);
		newEntity.setVelocityVector(new EntityLocation(newXVector, newYVector, newZVector));
	}
}
