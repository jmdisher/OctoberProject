package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This change moves an entity in the world.
 * This needs to account for a few different things:
 * -user-directed horizontal movement
 * -existing zVelocity of the entity due to falling/jumping
 * 
 * The complexity of needing to solve all of these issues within the change object is due to avoiding cheating and also
 * allowing responsive behaviour during high network latency or server load.  Points to consider:
 * -if the entity is falling, the server would be processing this ahead of the client, meaning it would never be able to
 *  submit a valid "before" location for the simpler "from-to" design (hence, falling is rationalized by the client and
 *  the server just checks it for validity and timeout cheating)
 * -if the entity jumps, it may take more than 1 tick for it to reach its zenith, meaning that the server would have no
 *  way to know if the entity is jumping, falling, or flying since it would just be starting a tick in mid-air
 * -during a fall, we need some way to know how quickly the entity is falling in order to have natural acceleration
 * 
 * Together, these problems are solved by making the zVelocity a property of the Entity and manipulating it in this
 * change.
 */
public class EntityChangeMove<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.MOVE;

	/**
	 * The flat distance that an entity can move in a single second.
	 * NOTE:  We currently operate using just axis-aligned movement, so no diagonals.
	 */
	public static final float ENTITY_MOVE_FLAT_LIMIT_PER_SECOND = 4.0f;
	public static final float ENTITY_MOVE_CLIMB_LIMIT_PER_SECOND = 2.0f;
	public static final float ENTITY_MOVE_FALL_LIMIT_PER_SECOND = 20.0f;
	public static final float GRAVITY_CHANGE_PER_SECOND = -9.8f;
	public static final float FALLING_TERMINAL_VELOCITY_PER_SECOND = -20.0f;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	/**
	 * Checks that this kind of move can be requested, given the time limits imposed by the change object.
	 * NOTE:  This doesn't mean that the move will succeed (could be a barrier, etc), just that it is well-formed.
	 * 
	 * @return True if this is an acceptable move.
	 */
	public static boolean isValidDistance(long millisBeforeMovement, float xDistance, float yDistance)
	{
		return _isValidDistance(millisBeforeMovement, xDistance, yDistance);
	}

	/**
	 * Calculates the number of milliseconds it will take to move the given distance.
	 * 
	 * @param xDistance The distance in x-axis.
	 * @param yDistance The distance in y-axis.
	 * @return The amount of time, in milliseconds.
	 */
	public static long getTimeMostMillis(float xDistance, float yDistance)
	{
		return _getTimeMostMillis(xDistance, yDistance);
	}

	public static <T extends IMutableMinimalEntity> EntityChangeMove<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation oldLocation = CodecHelpers.readEntityLocation(buffer);
		long millisBeforeMovement = buffer.getLong();
		float xDistance = buffer.getFloat();
		float yDistance = buffer.getFloat();
		return new EntityChangeMove<>(oldLocation, millisBeforeMovement, xDistance, yDistance);
	}

	/**
	 * Updates the given newEntity location to account for rising/falling motion, and updates the z-factor.
	 * 
	 * @param newEntity The entity to update.
	 * @param previousBlockLookUp The block look-up function.
	 * @param longMillisInMotion How many milliseconds of motion to consider.
	 * @return True if any motion change was applied, false if nothing changed or could change.
	 */
	public static boolean handleMotion(IMutableMinimalEntity newEntity
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, long longMillisInMotion
	)
	{
		return _handleMotion(newEntity, previousBlockLookUp, longMillisInMotion, 0.0f, 0.0f);
	}


	private final EntityLocation _oldLocation;
	private final long _millisBeforeMovement;
	private final float _xDistance;
	private final float _yDistance;

	public EntityChangeMove(EntityLocation oldLocation, long millisBeforeMovement, float xDistance, float yDistance)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(_isValidDistance(millisBeforeMovement, xDistance, yDistance));
		
		_oldLocation = oldLocation;
		_millisBeforeMovement = millisBeforeMovement;
		_xDistance = xDistance;
		_yDistance = yDistance;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisBeforeMovement + _getTimeMostMillis(_xDistance, _yDistance);
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		boolean oldDoesMatch = _oldLocation.equals(newEntity.getLocation());
		if (oldDoesMatch)
		{
			long millisInMotion = _millisBeforeMovement + _getTimeMostMillis(_xDistance, _yDistance);
			didApply = _handleMotion(newEntity, context.previousBlockLookUp, millisInMotion, _xDistance, _yDistance);
			
			if (didApply)
			{
				// Do other state reset now that we are moving.
				newEntity.resetLongRunningOperations();
			}
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _oldLocation);
		buffer.putLong(_millisBeforeMovement);
		buffer.putFloat(_xDistance);
		buffer.putFloat(_yDistance);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}


	private static boolean _isValidDistance(long millisBeforeMovement, float xDistance, float yDistance)
	{
		long costMillis = _getTimeMostMillis(xDistance, yDistance);
		return ((millisBeforeMovement + costMillis) <= LIMIT_COST_MILLIS);
	}

	private static long _getTimeMostMillis(float xDistance, float yDistance)
	{
		// TODO:  Change this when we allow diagonal movement.
		Assert.assertTrue((0.0f == xDistance) || (0.0f == yDistance));
		
		float xy = Math.abs(xDistance) + Math.abs(yDistance);
		float secondsFlat = (xy / ENTITY_MOVE_FLAT_LIMIT_PER_SECOND);
		return (long) (secondsFlat * 1000.0f);
	}

	private static boolean _handleMotion(IMutableMinimalEntity newEntity
			, Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, long longMillisInMotion
			, float xDistance
			, float yDistance
	)
	{
		// First of all, we need to figure out if we should be changing our z-vector:
		// -cancel positive vector if we hit the ceiling
		// -cancel negative vector if we hit the ground
		// -apply gravity in any other case
		float newZVector = newEntity.getZVelocityPerSecond();
		EntityLocation oldLocation = newEntity.getLocation();
		EntityVolume volume = newEntity.getVolume();
		float millisInMotion = (float)longMillisInMotion;
		if ((newZVector > 0.0f) && SpatialHelpers.isTouchingCeiling(previousBlockLookUp, oldLocation, volume))
		{
			// We are up against the ceiling so cancel the velocity.
			newZVector = 0.0f;
		}
		else if ((newZVector <= 0.0f) && SpatialHelpers.isStandingOnGround(previousBlockLookUp, oldLocation, volume))
		{
			// We are on the ground so cancel the velocity.
			newZVector = 0.0f;
		}
		else
		{
			// We are falling so update the velocity.
			float delta = GRAVITY_CHANGE_PER_SECOND / 1000.0f * millisInMotion;
			newZVector += delta;
			// Verify terminal velocity (we only apply this to falling).
			if (newZVector < FALLING_TERMINAL_VELOCITY_PER_SECOND)
			{
				newZVector = FALLING_TERMINAL_VELOCITY_PER_SECOND;
			}
		}
		
		// Figure out where our new location is (requires calculating the z-movement in this time).
		float risePerMilli = newZVector / 1000.0f;
		float zDistance = risePerMilli * millisInMotion;
		float oldZ = oldLocation.z();
		float xLocation = oldLocation.x() + xDistance;
		float yLocation = oldLocation.y() + yDistance;
		float zLocation = oldZ + zDistance;
		EntityLocation newLocation = new EntityLocation(xLocation, yLocation, zLocation);
		
		boolean didMove;
		if ((newEntity.getZVelocityPerSecond() == newZVector) && oldLocation.equals(newLocation))
		{
			// We don't want to apply this change if the entity isn't actually moving, since that will cause redundant update events.
			didMove = false;
		}
		else if (SpatialHelpers.canExistInLocation(previousBlockLookUp, newLocation, volume))
		{
			// They can exist in the target location so update the entity.
			newEntity.setLocationAndVelocity(newLocation, newZVector);
			didMove = true;
		}
		else
		{
			// This can happen when we bump into a wall (which would be a failure) but we will handle the special case of hitting the floor or ceiling and clamp the z.
			if (zDistance > 0.0f)
			{
				// We were jumping to see if we can clamp our location under the block.
				EntityLocation highestLocation = SpatialHelpers.locationTouchingCeiling(previousBlockLookUp, newLocation, volume, oldZ);
				if (null != highestLocation)
				{
					newEntity.setLocationAndVelocity(highestLocation, newZVector);
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
				EntityLocation lowestLocation = SpatialHelpers.locationTouchingGround(previousBlockLookUp, newLocation, volume, oldZ);
				if (null != lowestLocation)
				{
					newEntity.setLocationAndVelocity(lowestLocation, newZVector);
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
		return didMove;
	}
}
