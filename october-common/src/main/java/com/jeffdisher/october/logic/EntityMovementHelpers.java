package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
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
		EntityLocation oldVector = newEntity.getVelocityVector();
		float newX = xComponent * blocksPerSecond;
		float newY = yComponent * blocksPerSecond;
		newEntity.setVelocityVector(new EntityLocation(newX, newY, oldVector.z()));
		
		// TODO:  This is temporary until this helper can be removed.
		newEntity.applyEnergyCost(EntityChangePeriodic.ENERGY_COST_PER_TICK_WALKING);
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
		EntityVolume volume = newEntity.getType().volume();
		float initialZVector = oldVector.z();
		
		// We now just implement this in terms of interactive movement (it will eventually be removed, entirely).
		ViscosityReader reader = new ViscosityReader(env, previousBlockLookUp);
		float viscosityFraction = reader.getViscosityFraction(oldLocation.getBlockLocation());
		float inverseViscosity = (1.0f - viscosityFraction);
		
		float secondsInMotion = ((float)longMillisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float zVelocityChange = secondsInMotion * inverseViscosity * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
		float newZVelocity = initialZVector + zVelocityChange;
		float effectiveTerminalVelocity = inverseViscosity * MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
		if (newZVelocity < effectiveTerminalVelocity)
		{
			newZVelocity = effectiveTerminalVelocity;
		}
		
		// Note that we currently just set the x/y velocities to zero after applying the movement so just directly apply these through the viscosity.
		// TODO:  This assumption of setting x/y velocity to zero will need to change to support icy surfaces or "flying through the air".
		float velocityToApplyX = inverseViscosity * oldVector.x();
		float velocityToApplyY = inverseViscosity * oldVector.y();
		float velocityToApplyZ = (initialZVector + inverseViscosity * newZVelocity) / 2.0f;
		EntityLocation distanceToMove = new EntityLocation(secondsInMotion * velocityToApplyX
			, secondsInMotion * velocityToApplyY
			, secondsInMotion * velocityToApplyZ
		);
		final float finalZ = newZVelocity;
		
		_interactiveEntityMove(oldLocation, volume, distanceToMove, new InteractiveHelper() {
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
			{
				return reader.getViscosityFraction(location);
			}
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				// Set the location and velocity.
				newEntity.setLocation(finalLocation);
				// In this helper, we always cancel X/Y velocity but only Z if colliding.
				newEntity.setVelocityVector(new EntityLocation(0.0f
					, 0.0f
					, cancelZ ? 0.0f : finalZ
				));
			}
		});
	}

	/**
	 * Finds a path from start, along vectorToMove, using the interactive helper.  This will handle collisions with
	 * solid blocks to further constrain this path.  The final result is returned via the interactive helper.
	 * 
	 * @param start The base location of the entity.
	 * @param volume The volume of the entity.
	 * @param vectorToMove The direction the entity is trying to move, in relative coordinates.
	 * @param helper Used for looking up viscosities and setting final state.
	 */
	public static void interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, InteractiveHelper helper)
	{
		_interactiveEntityMove(start, volume, vectorToMove, helper);
	}

	/**
	 * Looks up the maximum viscosity of any block occupied by the given volume rooted at entityBase.
	 * 
	 * @param entityBase The base south-west-down corner of the space to check.
	 * @param volume The volume of the space to check.
	 * @param blockLookup The lookup helper for blocks.
	 * @return The maximum viscosity of any of the blocks in the requested space.
	 */
	public static float maxViscosityInEntityBlocks(EntityLocation entityBase, EntityVolume volume, Function<AbsoluteLocation, BlockProxy> blockLookup)
	{
		Environment env = Environment.getShared();
		InteractiveHelper helper = new InteractiveHelper() {
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
			{
				return new ViscosityReader(env, blockLookup).getViscosityFraction(location);
			}
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				// This isn't called in this path.
				throw Assert.unreachable();
			}
		};
		return _maxViscosityInEntityBlocks(entityBase, volume, helper);
	}


	private static boolean _canOccupyLocation(EntityLocation movingStart, EntityVolume volume, InteractiveHelper helper)
	{
		float maxViscosity = _maxViscosityInEntityBlocks(movingStart, volume, helper);
		boolean canOccupy = (maxViscosity < 1.0f);
		return canOccupy;
	}

	private static float _maxViscosityInEntityBlocks(EntityLocation entityBase, EntityVolume volume, InteractiveHelper helper)
	{
		EntityLocation entityEdge = new EntityLocation(entityBase.x() + volume.width()
			, entityBase.y() + volume.width()
			, entityBase.z() + volume.height()
		);
		AbsoluteLocation baseBlock = entityBase.getBlockLocation();
		AbsoluteLocation edgeBlock = entityEdge.getBlockLocation();
		
		float maxViscosity = 0.0f;
		for (AbsoluteLocation loc : new _VolumeIterator(baseBlock, edgeBlock))
		{
			float viscosity = helper.getViscosityForBlockAtLocation(loc);
			maxViscosity = Math.max(maxViscosity, viscosity);
		}
		return maxViscosity;
	}

	private static void _interactiveEntityMoveNotStuck(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, InteractiveHelper helper)
	{
		// We will move in the direction of vectorToMove, biased by viscosity, one block at a time until the move is complete or blocked in all movement directions.
		// Note that, for now at least, we will only compute the viscosity at the beginning of the move, as the move is rarely multiple blocks (falling at terminal velocity being an outlier example).
		
		// Generate the prism of the entity's current location.
		EntityLocation movingStart = start;
		EntityLocation edgeLocation = new EntityLocation(start.x() + volume.width()
			, start.y() + volume.width()
			, start.z() + volume.height()
		);
		Assert.assertTrue(volume.width() == EntityLocation.roundToHundredths(edgeLocation.y() - movingStart.y()));
		
		// We will create effective vector components which can be reduced as we manage collision/movement.
		float effectiveX = vectorToMove.x();
		float effectiveY = vectorToMove.y();
		float effectiveZ = vectorToMove.z();
		
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
			float moveX = EntityLocation.roundToHundredths(portion * effectiveX);
			float moveY = EntityLocation.roundToHundredths(portion * effectiveY);
			float moveZ = EntityLocation.roundToHundredths(portion * effectiveZ);
			
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
			case INTERNAL:
				// We don't expect to see this outside of the initial call due to how we cancel movement on collision per-block.
				throw Assert.unreachable();
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
			Assert.assertTrue(volume.width() == EntityLocation.roundToHundredths(edgeLocation.y() - movingStart.y()));
			
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
		helper.setLocationAndCancelVelocity(movingStart, cancelX, cancelY, cancelZ);
	}

	private static void _interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, InteractiveHelper helper)
	{
		// We want to handle the degenerate case of being stuck in a block first, because it avoids some setup and the 
		// common code assumes it isn't happening so will only report a partial collision.
		if (_canOccupyLocation(start, volume, helper))
		{
			// We aren't stuck so use the common case.
			_interactiveEntityMoveNotStuck(start, volume, vectorToMove, helper);
		}
		else
		{
			// We are already stuck here so just fail out without moving, colliding on all axes.
			helper.setLocationAndCancelVelocity(start, true, true, true);
		}
	}


	/**
	 * The interface used by interactiveEntityMove() in order to look up required information and return the final
	 * result.
	 */
	public static interface InteractiveHelper
	{
		/**
		 * Gets the viscosity of the block in the given location as a fraction (0.0f is no resistance while 1.0f is
		 * fully solid).
		 * 
		 * @param location The location to check.
		 * @return The viscosity fraction (1.0f being solid).
		 */
		public float getViscosityForBlockAtLocation(AbsoluteLocation location);
		/**
		 * The call issued by the interactive call to return all results instead of returning some kind of tuple.
		 * 
		 * @param finalLocation The location where the move stopped.
		 * @param cancelX True if there was a collision on the X axis (East/West).
		 * @param cancelY True if there was a collision on the Y axis (North/South).
		 * @param cancelZ True if there was a collision on the Z axis (Up/Down).
		 */
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ);
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
