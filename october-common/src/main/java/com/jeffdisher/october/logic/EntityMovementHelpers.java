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
		if ((initialZVector > 0.0f) && SpatialHelpers.isTouchingCeiling(previousBlockLookUp, oldLocation, volume))
		{
			// We are up against the ceiling so cancel the velocity.
			newZVector = 0.0f;
		}
		else if ((initialZVector <= 0.0f) && SpatialHelpers.isStandingOnGround(previousBlockLookUp, oldLocation, volume))
		{
			// We are on the ground so cancel the velocity.
			newZVector = 0.0f;
		}
		else
		{
			newZVector = MotionHelpers.applyZAcceleration(initialZVector, secondsInMotion);
		}
		
		// We will calculate the new z-vector based on gravity but only apply half to movement (since we assume acceleration is linear).
		float effectiveZVelocity = secondsInMotion * (initialZVector + newZVector) / 2.0f;
		
		// Note that we currently just set the x/y velocities to zero after applying the movement so just directly apply these through the viscosity.
		// TODO:  This assumption of setting x/y velocity to zero will need to change to support icy surfaces or "flying through the air".
		float velocityFraction = (1.0f - viscosityFraction);
		float velocityToApplyX = velocityFraction * oldVector.x();
		float velocityToApplyY = velocityFraction * oldVector.y();
		float velocityToApplyZ = velocityFraction * effectiveZVelocity;
		
		// We will calculate the new z-vector based on gravity but only apply half to movement (since we assume acceleration is linear).
		EntityLocation averageVelocity = new EntityLocation(secondsInMotion * velocityToApplyX, secondsInMotion * velocityToApplyY, velocityToApplyZ);
		// We also want to apply the fudge factor here to deal with the positive edge being exclusive to collision (this is subtracted later).
		float fudgeFactor = 0.01f;
		if (velocityToApplyX > 0.0f)
		{
			averageVelocity = new EntityLocation(averageVelocity.x() + fudgeFactor, averageVelocity.y(), averageVelocity.z());
		}
		if (velocityToApplyY > 0.0f)
		{
			averageVelocity = new EntityLocation(averageVelocity.x(), averageVelocity.y() + fudgeFactor, averageVelocity.z());
		}
		if (velocityToApplyZ > 0.0f)
		{
			averageVelocity = new EntityLocation(averageVelocity.x(), averageVelocity.y(), averageVelocity.z() + fudgeFactor);
		}
		
		// We will always cancel x/y velocity but only z if we cancelled.
		float newXVector = 0.0f;
		float newYVector = 0.0f;
		
		// We will try to move at most 3 times since we could collide in all 3 axes.
		EntityLocation zero = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation newLocation = oldLocation;
		EntityLocation lastAdjustment = oldLocation;
		while (!zero.equals(averageVelocity))
		{
			boolean[] ref_shouldCancel = new boolean[] {false};
			
			RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(newLocation, volume, averageVelocity, (AbsoluteLocation l) -> {
				boolean stop;
				BlockProxy proxy = previousBlockLookUp.apply(l);
				// This can be null if the world isn't totally loaded on the client.
				if (null != proxy)
				{
					stop = env.blocks.isSolid(proxy.getBlock());
				}
				else
				{
					stop = true;
				}
				if (stop)
				{
					// If we hit anything, we want to cancel the movement.
					ref_shouldCancel[0] = true;
				}
				return stop;
			});
			newLocation = result.location();
			
			// Adjust the location by removing the fudge factor (note that these are only on the positive edge).
			if (newLocation.x() > lastAdjustment.x())
			{
				newLocation = new EntityLocation(newLocation.x() - fudgeFactor, newLocation.y(), newLocation.z());
			}
			if (newLocation.y() > lastAdjustment.y())
			{
				newLocation = new EntityLocation(newLocation.x(), newLocation.y() - fudgeFactor, newLocation.z());
			}
			if (newLocation.z() > lastAdjustment.z())
			{
				newLocation = new EntityLocation(newLocation.x(), newLocation.y(), newLocation.z() - fudgeFactor);
			}
			
			// Account for how much of the velocity we have applied in this iteration, and store the last adjustment so we don't redundantly remove fudge factor.
			float deltaX = newLocation.x() - lastAdjustment.x();
			float deltaY = newLocation.y() - lastAdjustment.y();
			float deltaZ = newLocation.z() - lastAdjustment.z();
			lastAdjustment = newLocation;
			
			if (ref_shouldCancel[0] && (null != result.collisionAxis()))
			{
				// We hit something so figure out which direction to cancel.
				// Also, recalculate position to make sure we don't get stuck in a wall.
				switch (result.collisionAxis())
				{
				case X:
					averageVelocity = new EntityLocation(0.0f, averageVelocity.y() - deltaY, averageVelocity.z() - deltaZ);
					break;
				case Y:
					averageVelocity = new EntityLocation(averageVelocity.x() - deltaX, 0.0f, averageVelocity.z() - deltaZ);
					break;
				case Z:
					averageVelocity = new EntityLocation(averageVelocity.x() - deltaX, averageVelocity.y() - deltaY, 0.0f);
					newZVector = 0.0f;
					break;
				}
			}
			else
			{
				// We didn't hit anything, or we are stuck in a block, so just fall out.
				averageVelocity = zero;
			}
		}
		
		// Set the location and velocity.
		newEntity.setLocation(newLocation);
		newEntity.setVelocityVector(new EntityLocation(newXVector, newYVector, newZVector));
	}
}
