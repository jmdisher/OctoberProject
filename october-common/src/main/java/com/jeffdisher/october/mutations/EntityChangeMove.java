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
	 * Checks that this kind of move can be requested, given the time limits imposed by the change object.
	 * NOTE:  This doesn't mean that the move will succeed (could be a barrier, etc), just that it is well-formed.
	 * 
	 * @param speedBlocksPerSecond The speed of the moving entity, in blocks per second.
	 * @param xDistance The distance in x-axis.
	 * @param yDistance The distance in y-axis.
	 * @return True if this is an acceptable move.
	 */
	public static boolean isValidDistance(float speedBlocksPerSecond, float xDistance, float yDistance)
	{
		return _isValidDistance(speedBlocksPerSecond, xDistance, yDistance);
	}

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
		return _getTimeMostMillis(speedBlocksPerSecond, xDistance, yDistance);
	}

	public static <T extends IMutableMinimalEntity> EntityChangeMove<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		float speedBlocksPerSecond = buffer.getFloat();
		float xDistance = buffer.getFloat();
		float yDistance = buffer.getFloat();
		return new EntityChangeMove<>(speedBlocksPerSecond, xDistance, yDistance);
	}


	private final float _speedBlocksPerSecond;
	private final float _xDistance;
	private final float _yDistance;

	public EntityChangeMove(float speedBlocksPerSecond, float xDistance, float yDistance)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(_isValidDistance(speedBlocksPerSecond, xDistance, yDistance));
		
		_speedBlocksPerSecond = speedBlocksPerSecond;
		_xDistance = xDistance;
		_yDistance = yDistance;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _getTimeMostMillis(_speedBlocksPerSecond, _xDistance, _yDistance);
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		float maxSpeed = EntityConstants.getBlocksPerSecondSpeed(newEntity.getType());
		boolean isSpeedValid = (_speedBlocksPerSecond <= maxSpeed);
		if (isSpeedValid)
		{
			long millisInMotion = _getTimeMostMillis(_speedBlocksPerSecond, _xDistance, _yDistance);
			float xComponent = Math.signum(_xDistance);
			float yComponent = Math.signum(_yDistance);
			EntityMovementHelpers.accelerate(context, millisInMotion, newEntity, _speedBlocksPerSecond, millisInMotion, xComponent, yComponent);
			didApply = EntityMovementHelpers.allowMovement(context, newEntity, millisInMotion);
			
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
		buffer.putFloat(_speedBlocksPerSecond);
		buffer.putFloat(_xDistance);
		buffer.putFloat(_yDistance);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}


	private static boolean _isValidDistance(float speed, float xDistance, float yDistance)
	{
		long costMillis = _getTimeMostMillis(speed, xDistance, yDistance);
		return (costMillis > 0L) && (costMillis <= LIMIT_COST_MILLIS);
	}

	private static long _getTimeMostMillis(float speed, float xDistance, float yDistance)
	{
		// TODO:  Change this when we allow diagonal movement.
		Assert.assertTrue((0.0f == xDistance) || (0.0f == yDistance));
		
		float xy = Math.abs(xDistance) + Math.abs(yDistance);
		float secondsFlat = (xy / speed);
		return (long) (secondsFlat * 1000.0f);
	}
}
