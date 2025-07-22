package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityChangeMove<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_MOVE;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeMove<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		buffer.getLong();
		float speedMultiplier = buffer.getFloat();
		Direction direction = Direction.values()[buffer.get()];
		return new Deprecated_EntityChangeMove<>(speedMultiplier, direction);
	}


	private final float _speedMultipler;
	private final Direction _direction;

	@Deprecated
	public Deprecated_EntityChangeMove(float speedMultipler, Direction direction)
	{
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
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putLong(0L);
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
		return "Move " + _direction + " at " + _speedMultipler + " m/s";
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
