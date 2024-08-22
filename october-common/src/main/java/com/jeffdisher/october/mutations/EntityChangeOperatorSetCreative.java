package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity mutation for local use by an operator at the server console to set or clear the creative flag on an Entity.
 */
public class EntityChangeOperatorSetCreative implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.OPERATOR_SET_CREATIVE;

	public static EntityChangeOperatorSetCreative deserializeFromBuffer(ByteBuffer buffer)
	{
		// This is never serialized.
		throw Assert.unreachable();
	}


	private final boolean _setCreative;

	public EntityChangeOperatorSetCreative(boolean setCreative)
	{
		_setCreative = setCreative;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		newEntity.setCreativeMode(_setCreative);
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
		// This is never serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// It doesn't make sense to serialize this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Operator-SetCreative " + _setCreative;
	}
}
