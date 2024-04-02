package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * We wrap an IMutationEntity in IEntityUpdate for unit tests.
 */
public class EntityMutationWrapper implements IEntityUpdate
{
	private final IMutationEntity _mutation;

	public EntityMutationWrapper(IMutationEntity mutation)
	{
		_mutation = mutation;
	}

	@Override
	public void applyToEntity(TickProcessingContext context, MutableEntity newEntity)
	{
		// NOTE:  This line of code is why the TickProcessingContext is provided in this interface.  If tests change to not need it, it should be removed.
		_mutation.applyChange(context, newEntity);
	}

	@Override
	public EntityUpdateType getType()
	{
		// Not in test.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		// Not in test.
		throw Assert.unreachable();
	}
}
