package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This change does nothing but cancel any currently-running changes.
 * Note that this has a negative cost, which marks it as a "meta-change", so it is directly interpreted by the scheduler
 * in order to cancel a preceding change.
 * Note that the scheduler will still run this change if it did successfully cancel the preceding message (this means
 * that precisely 1 of the 2 will be run while the other will be dropped).
 */
public class EntityChangeCancel implements IMutationEntity
{
	@Override
	public long getTimeCostMillis()
	{
		// -1 is the special-case which allows this change to cancel any "in-progress" changes on the entity.
		return -1L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We don't want this passed to any clients so just fail.  This only runs to update the local commit level for the client.
		return false;
	}

	@Override
	public MutationEntityType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
