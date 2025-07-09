package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeAccelerate<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_ACCELERATE;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static final float MULTIPLIER_FORWARD = 1.0f;
	public static final float MULTIPLIER_STRAFE = 0.8f;
	public static final float MULTIPLIER_BACKWARD = 0.6f;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeAccelerate<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		Relative direction = Relative.values()[buffer.get()];
		return new Deprecated_EntityChangeAccelerate<>(millisInMotion, direction);
	}


	private final long _millisInMotion;
	private final Relative _direction;

	@Deprecated
	public Deprecated_EntityChangeAccelerate(long millisInMotion, Relative direction)
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
		// This is deprecated so just do nothing (only exists to read old data).
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
