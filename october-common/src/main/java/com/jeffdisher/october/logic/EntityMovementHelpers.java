package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Helper class containing the common logic around entity location/velocity and how they are updated with time and
 * validated against the impassable blocks in the environment.
 */
public class EntityMovementHelpers
{
	/**
	 * We apply a fudge factor to magnify the impact of the drag on vertical velocity.  This is done in order to make
	 * the drag behaviour "feel" right while the movement system is redesigned.
	 * TODO:  Remove this once the movement system and viscosity values are redone.
	 */
	public static final float DRAG_FUDGE_FACTOR = 10.0f;
	/**
	 * We back off from a "positive edge" using this fudge factor so that we don't get stuck "in" a block we are
	 * actually right up against.
	 */
	public static final float POSITIVE_EDGE_COLLISION_FUDGE_FACTOR = 0.01f;

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
				? env.blocks.getViscosityFraction(currentLocationProxy.getBlock(), FlagsAspect.isSet(currentLocationProxy.getFlags(), FlagsAspect.FLAG_ACTIVE))
				: 1.0f
		;
		float initialZVector = oldVector.z();
		EntityVolume volume = newEntity.getType().volume();
		float secondsInMotion = ((float)longMillisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float velocityFraction = (1.0f - viscosityFraction);
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
			float acceleratedZ = MotionHelpers.applyZAcceleration(initialZVector, secondsInMotion);
			// Decelerate the velocity based on time and drag.
			float drag = secondsInMotion * viscosityFraction * acceleratedZ * DRAG_FUDGE_FACTOR;
			newZVector = acceleratedZ - drag;
			// Make sure that this doesn't overflow (due to the drag fudge factor).
			if (Math.signum(acceleratedZ) != Math.signum(newZVector))
			{
				newZVector = 0.0f;
			}
		}
		
		// We will calculate the new z-vector based on gravity but only apply half to movement (since we assume acceleration is linear).
		float effectiveZVelocity = secondsInMotion * (initialZVector + newZVector) / 2.0f;
		
		// Note that we currently just set the x/y velocities to zero after applying the movement so just directly apply these through the viscosity.
		// TODO:  This assumption of setting x/y velocity to zero will need to change to support icy surfaces or "flying through the air".
		float velocityToApplyX = velocityFraction * oldVector.x();
		float velocityToApplyY = velocityFraction * oldVector.y();
		float velocityToApplyZ = velocityFraction * effectiveZVelocity;
		
		// We will calculate the new z-vector based on gravity but only apply half to movement (since we assume acceleration is linear).
		EntityLocation averageVelocity = new EntityLocation(secondsInMotion * velocityToApplyX, secondsInMotion * velocityToApplyY, velocityToApplyZ);
		// We also want to apply the fudge factor here to deal with the positive edge being exclusive to collision (this is subtracted later).
		float fudgeFactor = POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
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
			RayCastHelpers.RayMovement result = RayCastHelpers.applyMovement(newLocation, volume, averageVelocity, (AbsoluteLocation l) -> {
				boolean stop;
				BlockProxy proxy = previousBlockLookUp.apply(l);
				// This can be null if the world isn't totally loaded on the client.
				if (null != proxy)
				{
					boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
					stop = env.blocks.isSolid(proxy.getBlock(), isActive);
				}
				else
				{
					stop = true;
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
			
			// We want to cancel this if we stopped due to collision.
			if (null != result.collisionAxis())
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

	/**
	 * Finds a path from start, along vectorToMove, using the interactive helper.  This will internally account for
	 * block viscosity.  The final result is returned via the interactive helper.
	 * 
	 * @param start The base location of the entity.
	 * @param volume The volume of the entity.
	 * @param vectorToMove The direction the entity is trying to move, in relative coordinates.
	 * @param helper Used for looking up viscosities and setting final state.
	 */
	public static void interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, InteractiveHelper helper)
	{
		// We will move in the direction of vectorToMove, biased by viscosity, one block at a time until the move is complete or blocked in all movement directions.
		// Note that, for now at least, we will only compute the viscosity at the beginning of the move, as the move is rarely multiple blocks (falling at terminal velocity being an outlier example).
		
		// Generate the prism of the entity's current location.
		EntityLocation movingStart = start;
		AbsoluteLocation baseBlock = movingStart.getBlockLocation();
		EntityLocation edgeLocation = new EntityLocation(start.x() + volume.width()
			, start.y() + volume.width()
			, start.z() + volume.height()
		);
		AbsoluteLocation edgeBlock = edgeLocation.getBlockLocation();
		
		// Find the viscosity we will use to reduce the effective movement vector.
		// In the future, we could also track the trailing vertex and keep a count of active viscosities in the prism if we wanted to adjust viscosity mid-move (probably over-design since this rarely matters).
		float maxViscosity = 0.0f;
		for (AbsoluteLocation loc : new _VolumeIterator(baseBlock, edgeBlock))
		{
			float viscosity = helper.getViscosityForBlockAtLocation(loc);
			maxViscosity = Math.max(maxViscosity, viscosity);
		}
		// TODO:  Handle this quick failure case when we start out stuck in a block.
		Assert.assertTrue(maxViscosity < 1.0f);
		
		// Adjust the vector based on viscosity (linearly).
		float invosity = 1.0f - maxViscosity;
		float effectiveX = vectorToMove.x() * invosity;
		float effectiveY = vectorToMove.y() * invosity;
		float effectiveZ = vectorToMove.z() * invosity;
		
		// We will use a ray-cast from the corner of the prism in the direction of motion.
		float leadingX  = (effectiveX >  0.0f) ? edgeLocation.x() : start.x();
		float leadingY  = (effectiveY >  0.0f) ? edgeLocation.y() : start.y();
		float leadingZ  = (effectiveZ >  0.0f) ? edgeLocation.z() : start.z();
		EntityLocation leadingPoint = new EntityLocation(leadingX, leadingY, leadingZ);
		EntityLocation endOfVector = new EntityLocation(leadingX + effectiveX, leadingY + effectiveY, leadingZ + effectiveZ);
		AbsoluteLocation leadingBlock1 = leadingPoint.getBlockLocation();
		
		// Find the first block intersection of this point moving in the effective vector.
		RayCastHelpers.RayBlock collision = RayCastHelpers.findFirstCollision(leadingPoint, endOfVector, (AbsoluteLocation currentBlock) -> {
			return !leadingBlock1.equals(currentBlock);
		});
		// If we didn't collide, we are done.
		boolean cancelX = false;
		boolean cancelY = false;
		boolean cancelZ = false;
		while (null != collision)
		{
			// Move us over by the collision distance, check the block viscosities, and loop.
			float fullVectorLength = (float)Math.sqrt(effectiveX * effectiveX + effectiveY * effectiveY + effectiveZ * effectiveZ);
			float portion = collision.rayDistance() / fullVectorLength;
			float moveX = portion * effectiveX;
			float moveY = portion * effectiveY;
			float moveZ = portion * effectiveZ;
			
			// Adjust any positive movement to keep us within the existing block before we check if we can enter the others.
			if (moveX > 0.0f)
			{
				moveX -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			if (moveY > 0.0f)
			{
				moveY -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			if (moveZ > 0.0f)
			{
				moveZ -= POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
			}
			
			switch (collision.collisionAxis())
			{
			case X: {
				float dirX = (effectiveX > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX + dirX, movingStart.y() + moveY, movingStart.z() + moveZ);
				if (_canOccupyLocation(adjacent, volume, helper))
				{
					moveX += dirX;
				}
				else
				{
					effectiveX = 0.0f;
					cancelX = true;
				}
				break;
			}
			case Y: {
				float dirY = (effectiveY > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY + dirY, movingStart.z() + moveZ);
				if (_canOccupyLocation(adjacent, volume, helper))
				{
					moveY += dirY;
				}
				else
				{
					effectiveY = 0.0f;
					cancelY = true;
				}
				break;
			}
			case Z: {
				float dirZ = (effectiveZ > 0.0f) ? POSITIVE_EDGE_COLLISION_FUDGE_FACTOR : -POSITIVE_EDGE_COLLISION_FUDGE_FACTOR;
				EntityLocation adjacent = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY, movingStart.z() + moveZ + dirZ);
				if (_canOccupyLocation(adjacent, volume, helper))
				{
					moveZ += dirZ;
				}
				else
				{
					effectiveZ = 0.0f;
					cancelZ = true;
				}
				break;
			}
			default:
				throw Assert.unreachable();
			}
			leadingX += moveX;
			leadingY += moveY;
			leadingZ += moveZ;
			if (!cancelX)
			{
				effectiveX -= moveX;
			}
			if (!cancelY)
			{
				effectiveY -= moveY;
			}
			if (!cancelZ)
			{
				effectiveZ -= moveZ;
			}
			
			movingStart = new EntityLocation(movingStart.x() + moveX, movingStart.y() + moveY, movingStart.z() + moveZ);
			edgeLocation = new EntityLocation(edgeLocation.x() + moveX, edgeLocation.y() + moveY, edgeLocation.z() + moveZ);
			
			leadingPoint = new EntityLocation(leadingX, leadingY, leadingZ);
			endOfVector = new EntityLocation(leadingX + effectiveX, leadingY + effectiveY, leadingZ + effectiveZ);
			AbsoluteLocation leadingBlock = leadingPoint.getBlockLocation();
			collision = RayCastHelpers.findFirstCollision(leadingPoint, endOfVector, (AbsoluteLocation currentBlock) -> {
				return !leadingBlock.equals(currentBlock);
			});
		}
		// If we didn't collide, we must have reached the end of the vector (make sure we get the position).
		movingStart = new EntityLocation(movingStart.x() + effectiveX
			, movingStart.y() + effectiveY
			, movingStart.z() + effectiveZ
		);
		helper.setLocationAndViscosity(movingStart, maxViscosity, cancelX, cancelY, cancelZ);
	}


	private static boolean _canOccupyLocation(EntityLocation movingStart, EntityVolume volume, InteractiveHelper helper)
	{
		AbsoluteLocation baseBlock = movingStart.getBlockLocation();
		EntityLocation edgeLocation = new EntityLocation(movingStart.x() + volume.width()
			, movingStart.y() + volume.width()
			, movingStart.z() + volume.height()
		);
		AbsoluteLocation edgeBlock = edgeLocation.getBlockLocation();
		boolean canOccupy = true;
		for (AbsoluteLocation loc : new _VolumeIterator(baseBlock, edgeBlock))
		{
			float viscosity = helper.getViscosityForBlockAtLocation(loc);
			if (1.0f == viscosity)
			{
				canOccupy = false;
				break;
			}
		}
		return canOccupy;
	}


	/**
	 * The interface used by interactiveEntityMove() in order to look up required information and return the final
	 * result.
	 */
	public static interface InteractiveHelper
	{
		public float getViscosityForBlockAtLocation(AbsoluteLocation location);
		public void setLocationAndViscosity(EntityLocation finalLocation, float viscosity, boolean cancelX, boolean cancelY, boolean cancelZ);
	}


	private static class _VolumeIterator implements Iterable<AbsoluteLocation>
	{
		private final AbsoluteLocation _start;
		private final AbsoluteLocation _end;
		
		public _VolumeIterator(AbsoluteLocation start, AbsoluteLocation end)
		{
			_start = start;
			_end = end;
		}
		
		@Override
		public Iterator<AbsoluteLocation> iterator()
		{
			return new Iterator<>() {
				private final int _startX = _start.x();
				private final int _startY = _start.y();
				private final int _startZ = _start.z();
				private final int _endX = _end.x();
				private final int _endY = _end.y();
				private final int _endZ = _end.z();
				private int _x = _startX;
				private int _y = _startY;
				private int _z = _startZ;
				
				@Override
				public boolean hasNext()
				{
					return (_x <= _endX) && (_y <= _endY) && (_z <= _endZ);
				}
				
				@Override
				public AbsoluteLocation next()
				{
					AbsoluteLocation next;
					if ((_x <= _endX) && (_y <= _endY) && (_z <= _endZ))
					{
						next = new AbsoluteLocation(_x, _y, _z);
					}
					else
					{
						// This would be a failure of hasNext().
						throw Assert.unreachable();
					}
					
					_x += 1;
					if (_x > _endX)
					{
						_x = _startX;
						_y += 1;
						if (_y > _endY)
						{
							_y = _startY;
							_z += 1;
						}
					}
					return next;
				}
			};
		}
	}
}
