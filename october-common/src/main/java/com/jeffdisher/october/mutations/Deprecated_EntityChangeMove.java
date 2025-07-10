package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeMove<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_MOVE;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeMove<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		float speedMultiplier = buffer.getFloat();
		Direction direction = Direction.values()[buffer.get()];
		return new Deprecated_EntityChangeMove<>(millisInMotion, speedMultiplier, direction);
	}


	private final long _millisInMotion;
	private final float _speedMultipler;
	private final Direction _direction;

	@Deprecated
	public Deprecated_EntityChangeMove(long millisInMotion, float speedMultipler, Direction direction)
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
