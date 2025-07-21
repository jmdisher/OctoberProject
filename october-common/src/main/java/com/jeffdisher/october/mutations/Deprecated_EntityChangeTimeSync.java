package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityChangeTimeSync implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_TIME_SYNC_NOOP;

	public static Deprecated_EntityChangeTimeSync deserializeFromBuffer(ByteBuffer buffer)
	{
		buffer.getLong();
		return new Deprecated_EntityChangeTimeSync();
	}


	@Deprecated
	public Deprecated_EntityChangeTimeSync()
	{
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
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
		return "Time sync";
	}
}
