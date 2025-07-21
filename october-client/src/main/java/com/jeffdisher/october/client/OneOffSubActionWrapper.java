package com.jeffdisher.october.client;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Used when verifying a sub-action with the OneOffRunner before using it in an actual accumulation.
 */
public class OneOffSubActionWrapper implements IEntityAction<IMutablePlayerEntity>
{
	private final IEntitySubAction<IMutablePlayerEntity> _sub;

	public OneOffSubActionWrapper(IEntitySubAction<IMutablePlayerEntity> sub)
	{
		_sub = sub;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		return _sub.applyChange(context, newEntity);
	}

	@Override
	public EntityActionType getType()
	{
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		throw Assert.unreachable();
	}
}
