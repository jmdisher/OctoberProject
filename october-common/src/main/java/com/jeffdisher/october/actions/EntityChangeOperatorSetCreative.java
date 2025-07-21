package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An entity mutation for local use by an operator at the server console to set or clear the creative flag on an Entity.
 */
public class EntityChangeOperatorSetCreative implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.OPERATOR_SET_CREATIVE;

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
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		newEntity.setCreativeMode(_setCreative);
		// We need to clear the hotbar since this is changing the interpretation of the inventory (we may want to change how we access this).
		for (int key : newEntity.copyHotbar())
		{
			if (key > 0)
			{
				newEntity.clearHotBarWithKey(key);
			}
		}
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
