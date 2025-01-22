package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This change moves an entity in the world.
 * Internally, it just sets the velocity vector so that the engine will apply that velocity for the amount of time
 * described by this.
 * 
 * Note that this change interprets the direction relative to the entity's current orientation and applies different
 * speeds based on the facing direction.
 */
public class EntityChangeAccelerate<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.ACCELERATE;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static final float MULTIPLIER_FORWARD = 1.0f;
	public static final float MULTIPLIER_STRAFE = 0.8f;
	public static final float MULTIPLIER_BACKWARD = 0.6f;

	public static <T extends IMutableMinimalEntity> EntityChangeAccelerate<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		Relative direction = Relative.values()[buffer.get()];
		return new EntityChangeAccelerate<>(millisInMotion, direction);
	}


	private final long _millisInMotion;
	private final Relative _direction;

	public EntityChangeAccelerate(long millisInMotion, Relative direction)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(millisInMotion > 0L);
		Assert.assertTrue(millisInMotion <= LIMIT_COST_MILLIS);
		
		_millisInMotion = millisInMotion;
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
		// Find our speed and determine the components of movement.
		float maxSpeed = newEntity.getType().blocksPerSecond();
		float orientationRadians = OrientationHelpers.getYawRadians(newEntity.getYaw());
		float yawRadians = orientationRadians + _direction.yawRadians;
		float xComponent = OrientationHelpers.getEastYawComponent(yawRadians);
		float yComponent = OrientationHelpers.getNorthYawComponent(yawRadians);
		float speed = maxSpeed * _direction.speedMultiplier;
		EntityMovementHelpers.accelerate(newEntity, speed, _millisInMotion, xComponent, yComponent);
		return true;
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
		return "Accelerate " + _direction + " for " + _millisInMotion + " ms";
	}


	/**
	 * The direction of a horizontal move, relative to the orientation.
	 */
	public static enum Relative
	{
		FORWARD(MULTIPLIER_FORWARD, 0.0f),
		RIGHT(MULTIPLIER_STRAFE, (float)(3.0 / 2.0 * Math.PI)),
		LEFT(MULTIPLIER_STRAFE, (float)(1.0 / 2.0 * Math.PI)),
		BACKWARD(MULTIPLIER_BACKWARD, (float)Math.PI),
		;
		public final float speedMultiplier;
		public final float yawRadians;
		private Relative(float speedMultiplier, float yawRadians)
		{
			this.speedMultiplier = speedMultiplier;
			this.yawRadians = yawRadians;
		}
	}
}
