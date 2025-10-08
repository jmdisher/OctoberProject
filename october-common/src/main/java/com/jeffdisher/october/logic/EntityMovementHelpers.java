package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
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
	 * We will use standard Earth gravity -9.8 ms^2.
	 */
	public static final float GRAVITY_CHANGE_PER_SECOND = -9.8f;
	/**
	 * The terminal velocity when falling through air.
	 * (40 m/s seems to be a common free-fall velocity for sky-divers)
	 */
	public static final float AIR_TERMINAL_VELOCITY_PER_SECOND = 40.0f;
	/**
	 * The terminal velocity when falling through air.
	 * (40 m/s seems to be a common free-fall velocity for sky-divers)
	 */
	public static final float FALLING_TERMINAL_VELOCITY_PER_SECOND = -AIR_TERMINAL_VELOCITY_PER_SECOND;
	/**
	 * Just a helpful constant.
	 */
	public static final float FLOAT_MILLIS_PER_SECOND = 1000.0f;

	/**
	 * Finds a path from start, along vectorToMove, using the interactive helper.  This will handle collisions with
	 * solid blocks to further constrain this path.  The final result is returned via the interactive helper.
	 * 
	 * @param start The base location of the entity.
	 * @param volume The volume of the entity.
	 * @param vectorToMove The direction the entity is trying to move, in relative coordinates.
	 * @param helper Used for looking up viscosities and setting final state.
	 */
	public static void interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper)
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
		ViscosityReader reader = new ViscosityReader(env, blockLookup);
		IViscosityLookup helper = (AbsoluteLocation location, boolean fromAbove) -> reader.getViscosityFraction(location, fromAbove);
		
		// In this case, we are just check where we stand, not falling.
		boolean fromAbove = false;
		return _maxViscosityInEntityBlocks(entityBase, volume, helper, fromAbove);
	}

	/**
	 * Used to check if the entity with volume at entityBase is intersecting a specific type of block based on
	 * caller-defined criteria.
	 * 
	 * @param entityBase The base south-west-down corner of the space to check.
	 * @param volume The volume of the space to check.
	 * @param predicate Returns true if the function should this location.
	 * @return The first location where the predicate returned true, or null if it never did.
	 */
	public static AbsoluteLocation checkTypeIntersection(EntityLocation entityBase, EntityVolume volume, Predicate<AbsoluteLocation> predicate)
	{
		_VolumeIterator iterator = _iteratorForEntity(entityBase, volume);
		
		AbsoluteLocation match = null;
		for (AbsoluteLocation loc : iterator)
		{
			if (predicate.test(loc))
			{
				match = loc;
				break;
			}
		}
		return match;
	}

	/**
	 * The common movement idiom, extracted from EntityActionSimpleMove in order to allow reuse and improve test
	 * coverage.
	 * Given a start location and velocity, plus explicit movement on top of that, this helper computes the final
	 * location and velocity.
	 * 
	 * @param previousBlockLookUp Looks up blocks from the previous tick.
	 * @param startLocation The starting base location of the entity.
	 * @param startVelocity The starting velocity of the entity.
	 * @param volume The volume of the entity.
	 * @param activeXMovement Absolute active movement in the X direction (in total blocks travelled in seconds).
	 * @param activeYMovement Absolute active movement in the Y direction (in total blocks travelled in seconds).
	 * @param maxVelocityPerSecond The maximum velocity of the entity, accounting for how intensely it is moving.
	 * @param seconds The number of seconds elapsed while the active movement was applied.
	 * @return The final location and components of velocity.
	 */
	public static HighLevelMovementResult commonMovementIdiom(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
		, EntityLocation startLocation
		, EntityLocation startVelocity
		, EntityVolume volume
		, float activeXMovement
		, float activeYMovement
		, float maxVelocityPerSecond
		, float seconds
	)
	{
		Environment env = Environment.getShared();
		ViscosityReader reader = new ViscosityReader(env, previousBlockLookUp);
		
		// In this case, we are just check where we stand, not falling.
		IViscosityLookup viscosityLookup = (AbsoluteLocation location, boolean fromAbove) -> reader.getViscosityFraction(location, fromAbove);
		boolean fromAbove = false;
		float startViscosity = _maxViscosityInEntityBlocks(startLocation, volume, viscosityLookup, fromAbove);
		EntityLocation effectiveVelocity = _adjustVelocityForMovement(startVelocity, activeXMovement, activeYMovement, maxVelocityPerSecond, seconds, startViscosity);
		
		// Derive the effective movement vector for this action.
		EntityLocation effectiveMovement = new EntityLocation(seconds * effectiveVelocity.x()
			, seconds * effectiveVelocity.y()
			, seconds * effectiveVelocity.z()
		);
		
		// Apply the effective movement to find collisions.
		HighLevelMovementResult[] out = new HighLevelMovementResult[1];
		IInteractiveHelper helper = new IInteractiveHelper()
		{
			@Override
			public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				// Derive final velocity by checking these collisions (note that we also account for XY surface friction if on the ground).
				boolean isOnGround = false;
				float finX = effectiveVelocity.x();
				float finY = effectiveVelocity.y();
				float finZ = effectiveVelocity.z();
				if (cancelZ)
				{
					finZ = 0.0f;
					isOnGround = (effectiveMovement.z() <= 0.0f);
				}
				if (cancelX || isOnGround)
				{
					finX = 0.0f;
				}
				if (cancelY || isOnGround)
				{
					finY = 0.0f;
				}
				out[0] = new HighLevelMovementResult(finalLocation, finX, finY, finZ);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove)
			{
				return reader.getViscosityFraction(location, fromAbove);
			}
		};
		_interactiveEntityMove(startLocation, volume, effectiveMovement, helper);
		return out[0];
	}


	private static boolean _canOccupyLocation(EntityLocation movingStart, EntityVolume volume, IViscosityLookup helper, boolean fromAbove)
	{
		float maxViscosity = _maxViscosityInEntityBlocks(movingStart, volume, helper, fromAbove);
		boolean canOccupy = (maxViscosity < 1.0f);
		return canOccupy;
	}

	private static float _maxViscosityInEntityBlocks(EntityLocation entityBase, EntityVolume volume, IViscosityLookup helper, boolean fromAbove)
	{
		_VolumeIterator iterator = _iteratorForEntity(entityBase, volume);
		
		float maxViscosity = 0.0f;
		for (AbsoluteLocation loc : iterator)
		{
			float viscosity = helper.getViscosityForBlockAtLocation(loc, fromAbove);
			maxViscosity = Math.max(maxViscosity, viscosity);
		}
		return maxViscosity;
	}

	private static void _interactiveEntityMoveNotStuck(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper, boolean failZ)
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
		boolean cancelZ = failZ;
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
				if (_canOccupyLocation(adjacent, volume, helper, false))
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
				if (_canOccupyLocation(adjacent, volume, helper, false))
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
				boolean fromAbove = (dirZ < 0.0f);
				if (_canOccupyLocation(adjacent, volume, helper, fromAbove))
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

	private static void _interactiveEntityMove(EntityLocation start, EntityVolume volume, EntityLocation vectorToMove, IInteractiveHelper helper)
	{
		// We want to handle the degenerate case of being stuck in a block first, because it avoids some setup and the 
		// common code assumes it isn't happening so will only report a partial collision.
		boolean fromAbove = true;
		boolean failZ = false;
		if ((vectorToMove.z() < 0.0f) && !_canOccupyLocation(start, volume, helper, fromAbove))
		{
			// Cancel the z-movement.
			vectorToMove = new EntityLocation(vectorToMove.x(), vectorToMove.y(), 0.0f);
			failZ = true;
		}
		fromAbove = false;
		if (_canOccupyLocation(start, volume, helper, fromAbove))
		{
			// We aren't stuck so use the common case.
			_interactiveEntityMoveNotStuck(start, volume, vectorToMove, helper, failZ);
		}
		else
		{
			// We are already stuck here so just fail out without moving, colliding on all axes.
			helper.setLocationAndCancelVelocity(start, true, true, true);
		}
	}

	private static _VolumeIterator _iteratorForEntity(EntityLocation entityBase, EntityVolume volume)
	{
		EntityLocation entityEdge = new EntityLocation(entityBase.x() + volume.width()
			, entityBase.y() + volume.width()
			, entityBase.z() + volume.height()
		);
		AbsoluteLocation baseBlock = entityBase.getBlockLocation();
		AbsoluteLocation edgeBlock = entityEdge.getBlockLocation();
		
		return new _VolumeIterator(baseBlock, edgeBlock);
	}

	private static float _clampByAirTerminal(float v)
	{
		float out;
		if (Math.abs(v) > EntityMovementHelpers.AIR_TERMINAL_VELOCITY_PER_SECOND)
		{
			out = Math.signum(v) * EntityMovementHelpers.AIR_TERMINAL_VELOCITY_PER_SECOND;
		}
		else
		{
			out = v;
		}
		return out;
	}

	private static float _clampHorizontalAcceleration(float passive, float active, float intensityVelocityPerSecond)
	{
		float sum;
		if (Math.signum(passive) == Math.signum(active))
		{
			// We need to account for various clamping here.
			if (Math.abs(passive) > intensityVelocityPerSecond)
			{
				sum = Math.signum(passive) * intensityVelocityPerSecond;
			}
			else
			{
				sum = passive + active;
				if (Math.abs(sum) > intensityVelocityPerSecond)
				{
					sum = Math.signum(sum) * intensityVelocityPerSecond;
				}
			}
		}
		else
		{
			// This is deceleration so just sum.
			sum = passive + active;
		}
		return sum;
	}

	private static EntityLocation _adjustVelocityForMovement(EntityLocation inputVelocity, float activeXMovement, float activeYMovement, float maxVelocityPerSecond, float seconds, float startViscosity)
	{
		// We calculate the effective velocity at the start of the action (which will be applied and further refined later):
		// 1) Convert XY movement, and gravity, into velocity per second.
		// 2) Add existing velocity to new velocity (note that XY cannot push the velocity over user maximum).
		// 3) Clamp new velocity to terminal velocity in air.
		// 4) Determine starting viscosity and multiply inverse viscosity against this clamped velocity.
		// The result is our effective starting velocity.
		
		float passiveX = inputVelocity.x();
		float passiveY = inputVelocity.y();
		float passiveZ = inputVelocity.z();
		
		float activeVX = activeXMovement / seconds;
		float activeVY = activeYMovement / seconds;
		float activeVZ = EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND * seconds;
		
		// We want to limit XY velocity from this active movement to the maximum of the entity's velocity.
		float sumVX = _clampHorizontalAcceleration(passiveX, activeVX, maxVelocityPerSecond);
		float sumVY = _clampHorizontalAcceleration(passiveY, activeVY, maxVelocityPerSecond);
		float sumVZ = passiveZ + activeVZ;
		
		// We now clamp everything by air terminal velocity.
		float airX = _clampByAirTerminal(sumVX);
		float airY = _clampByAirTerminal(sumVY);
		float airZ = _clampByAirTerminal(sumVZ);
		
		// Finally, multiply these clamped values by the inverse viscosity of the starting block.
		// TODO:  We probably need to rework this approach to drag, in the future.
		float startInverseViscosity = 1.0f - startViscosity;
		float visX = startInverseViscosity * airX;
		float visY = startInverseViscosity * airY;
		float visZ = startInverseViscosity * airZ;
		return new EntityLocation(visX, visY, visZ);
	}


	public static interface IViscosityLookup
	{
		/**
		 * Gets the viscosity of the block in the given location as a fraction (0.0f is no resistance while 1.0f is
		 * fully solid).
		 * 
		 * @param location The location to check.
		 * @param fromAbove True if we are asking for viscosity while falling into the block, false for other
		 * collisions.
		 * @return The viscosity fraction (1.0f being solid).
		 */
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove);
	}

	/**
	 * The interface used by interactiveEntityMove() in order to look up required information and return the final
	 * result.
	 */
	public static interface IInteractiveHelper extends IViscosityLookup
	{
		/**
		 * Gets the viscosity of the block in the given location as a fraction (0.0f is no resistance while 1.0f is
		 * fully solid).
		 * 
		 * @param location The location to check.
		 * @param fromAbove True if we are asking for viscosity while falling into the block, false for other
		 * collisions.
		 * @return The viscosity fraction (1.0f being solid).
		 */
		public float getViscosityForBlockAtLocation(AbsoluteLocation location, boolean fromAbove);
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

	/**
	 * Used to return the final result of commonMovementIdiom().
	 * The location is provided directly but the velocity is kept as components in case it must be further processed.
	 */
	public static record HighLevelMovementResult(EntityLocation location, float vX, float vY, float vZ) {}


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
