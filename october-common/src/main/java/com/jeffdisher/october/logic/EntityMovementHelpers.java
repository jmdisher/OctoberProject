package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
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
	 * Accelerates the given newEntity by adding blocksPerSecond for secondsInMotion split across the given xComponent
	 * and yComponent to its existing velocity vector. Note that this assumes that the velocity will be applied once
	 * per tick so it accounts for tick time to determine how to scale this velocity change for that observation.
	 * Note:  No check is done on xComponent or yComponent but they should typically describe a combined vector of 1.0f
	 * length.
	 * This helper will also apply the movement energy cost to the entity.
	 * 
	 * @param context The context.
	 * @param secondsInMotion How many seconds of motion to consider.
	 * @param newEntity The entity to update.
	 * @param blocksPerSecond The speed of the entity for this time.
	 * @param millisPerTick The number of milliseconds between "allowMovement()" calls.
	 * @param xComponent The x-component of the motion (note that sqrt(x^2 + y ^2) should probably be <= 1.0).
	 * @param yComponent The y-component of the motion (note that sqrt(x^2 + y ^2) should probably be <= 1.0).
	 */
	public static void accelerate(TickProcessingContext context
			, long millisPerTick
			, IMutableMinimalEntity newEntity
			, float blocksPerSecond
			, long millisInMotion
			, float xComponent
			, float yComponent
	)
	{
		// First set the velocity.
		float secondsPerTick = ((float)millisPerTick) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float secondsInMotion = ((float)millisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		EntityLocation oldVector = newEntity.getVelocityVector();
		float targetBlocksInStep = (blocksPerSecond * secondsInMotion);
		float totalMotion = targetBlocksInStep / secondsPerTick;
		float newX = oldVector.x() + (xComponent * totalMotion);
		float newY = oldVector.y() + (yComponent * totalMotion);
		newEntity.setVelocityVector(new EntityLocation(newX, newY, oldVector.z()));
		
		// Then pay for the acceleration.
		int cost = (int)(targetBlocksInStep * (float)EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK);
		newEntity.applyEnergyCost(context, cost);
	}

	/**
	 * Updates the given newEntity location to account for passive rising/falling motion, and updates the z-factor.
	 * Updates newEntity's location, velocity, and energy usage.
	 * Note that this will reduce the horizontal velocity of newEntity to 0.0 (since we currently only have maximum
	 * friction on all blocks and also want to stop air drift immediately - not realistic but keeps this simple and
	 * makes the user feedback be what they would expect in a video game).
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
		Environment env = Environment.getShared();
		EntityLocation oldVector = newEntity.getVelocityVector();
		EntityLocation oldLocation = newEntity.getLocation();
		
		// First of all, we need to figure out if we should be changing our z-vector:
		// -apply viscosity to the previous vector (direct multiplier)
		// -cancel positive vector if we hit the ceiling
		// -cancel negative vector if we hit the ground
		// -apply gravity in any other case
		BlockProxy currentLocationProxy = context.previousBlockLookUp.apply(oldLocation.getBlockLocation());
		int currentBlockViscosity = (null != currentLocationProxy)
				? env.blocks.blockViscosity(currentLocationProxy.getBlock())
				: BlockAspect.SOLID_VISCOSITY
		;
		float viscosityMultiplier = (float)(BlockAspect.SOLID_VISCOSITY - currentBlockViscosity) / (float)BlockAspect.SOLID_VISCOSITY;
		EntityLocation vector = new EntityLocation(viscosityMultiplier * oldVector.x(), viscosityMultiplier * oldVector.y(), viscosityMultiplier * oldVector.z());
		float initialZVector = vector.z();
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
