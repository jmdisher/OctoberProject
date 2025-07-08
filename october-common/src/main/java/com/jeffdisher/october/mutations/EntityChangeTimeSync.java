package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Does nothing.  This only exists to take up scheduling time on the server and be given a commit number on the client
 * so things like falling are synchronized between them.
 * NOTE:  This was deprecated and removed in NETWORK_PROTOCOL_VERSION 9.
 */
public class EntityChangeTimeSync implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_TIME_SYNC_NOOP;

	public static EntityChangeTimeSync deserializeFromBuffer(ByteBuffer buffer)
	{
		long millisInMotion = buffer.getLong();
		return new EntityChangeTimeSync(millisInMotion);
	}


	private final long _millisInMotion;

	public EntityChangeTimeSync(long millisInMotion)
	{
		Assert.assertTrue(millisInMotion > 0L);
		
		_millisInMotion = millisInMotion;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisInMotion;
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
