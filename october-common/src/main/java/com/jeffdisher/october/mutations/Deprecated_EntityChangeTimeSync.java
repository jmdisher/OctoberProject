package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeTimeSync implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_TIME_SYNC_NOOP;

	public static Deprecated_EntityChangeTimeSync deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		return new Deprecated_EntityChangeTimeSync(millisInMotion);
	}


	private final long _millisInMotion;

	@Deprecated
	public Deprecated_EntityChangeTimeSync(long millisInMotion)
	{
		Assert.assertTrue(millisInMotion > 0L);
		
		_millisInMotion = millisInMotion;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// We do nothing but return success after consuming the time.
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
		return "Time sync for " + _millisInMotion + " ms";
	}
}
