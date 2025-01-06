package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity mutation which just requests that a block mutation be scheduled in the future.
 */
public class EntityChangeFutureBlock implements IMutationEntity<IMutablePlayerEntity>
{
	private final IMutationBlock _mutation;
	private final long _millisDelay;

	public EntityChangeFutureBlock(IMutationBlock mutation, long millisDelay)
	{
		_mutation = mutation;
		_millisDelay = millisDelay;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		context.mutationSink.future(_mutation, _millisDelay);
		return true;
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

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
