package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is a special change implicitly added by the TickRunner and run against all entities at the end of a tick.
 * It doesn't currently do anything but will be used (and has been used) to apply per-tick environmental rules (think
 * starvation, etc).
 * Since this is added by the server, internally, it should reject any attempt made by the client to explicitly send it.
 * Additionally, the same instance is reused so it must be stateless.
 */
public class EntityChangeImplicit implements IMutationEntity
{
	@Override
	public long getTimeCostMillis()
	{
		// This is never called since these aren't scheduled but implicitly run.
		throw Assert.unreachable();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// If this does anything, we want to say it applied so we will send it to the clients.
		boolean didApply = false;
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		// Not yet used.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not yet used.
		throw Assert.unreachable();
	}
}
