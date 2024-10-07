package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.types.EntityConstants;
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
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;
	/**
	 * Can be multiplied against a per-second speed value to determine the maximum allowed by one mutation.
	 */
	public static final float MAX_PER_STEP_SPEED_MULTIPLIER = LIMIT_COST_MILLIS / 1000.0f;

	/**
	 * Calculates the number of milliseconds it will take to move the given distance.
	 * 
	 * @param speedBlocksPerSecond The speed of the moving entity, in blocks per second.
	 * @param xDistance The distance in x-axis.
	 * @param yDistance The distance in y-axis.
	 * @return The amount of time, in milliseconds.
	 */
	public static long getTimeMostMillis(float speedBlocksPerSecond, float xDistance, float yDistance)
	{
		long millis = 0L;
		float totalDistance = (float)Math.sqrt((xDistance * xDistance) + (yDistance * yDistance));
		if (totalDistance > 0.0f)
		{
			float secondsToMove = totalDistance / speedBlocksPerSecond;
			millis = Math.round(secondsToMove * 1000.0f);
		}
		return millis;
	}

	public static <T extends IMutableMinimalEntity> EntityChangeMove<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		float speedMultiplier = buffer.getFloat();
		Direction direction = Direction.values()[buffer.get()];
		return new EntityChangeMove<>(millisInMotion, speedMultiplier, direction);
	}


	private final long _millisInMotion;
	private final float _speedMultipler;
	private final Direction _direction;

	public EntityChangeMove(long millisInMotion, float speedMultipler, Direction direction)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(millisInMotion > 0L);
		Assert.assertTrue(millisInMotion <= LIMIT_COST_MILLIS);
		
		_millisInMotion = millisInMotion;
		_speedMultipler = speedMultipler;
		_direction = direction;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisInMotion;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		if ((_millisInMotion > 0) && (_millisInMotion <= LIMIT_COST_MILLIS))
		{
			// Find our speed and determine the components of movement.
			float maxSpeed = EntityConstants.getBlocksPerSecondSpeed(newEntity.getType());
			float xComponent;
			float yComponent;
			switch (_direction)
			{
			case NORTH:
				xComponent = 0.0f;
				yComponent = 1.0f;
				break;
			case EAST:
				xComponent = 1.0f;
				yComponent = 0.0f;
				break;
			case SOUTH:
				xComponent = 0.0f;
				yComponent = -1.0f;
				break;
			case WEST:
				xComponent = -1.0f;
				yComponent = 0.0f;
				break;
			default:
				throw Assert.unreachable();
			}
			float speed = maxSpeed * _speedMultipler;
			EntityMovementHelpers.accelerate(newEntity, speed, _millisInMotion, xComponent, yComponent);
			didApply = true;
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
		buffer.putLong(_millisInMotion);
		buffer.putFloat(_speedMultipler);
		buffer.put((byte)_direction.ordinal());
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Move " + _direction + " for " + _millisInMotion + " ms at " + _speedMultipler + " m/s";
	}


	/**
	 * The direction of a horizontal move.
	 */
	public static enum Direction
	{
		NORTH,
		EAST,
		SOUTH,
		WEST,
	}
}
