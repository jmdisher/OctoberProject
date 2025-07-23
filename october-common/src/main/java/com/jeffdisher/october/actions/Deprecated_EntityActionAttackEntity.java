package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityActionAttackEntity implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_ATTACK_ENTITY;

	public static Deprecated_EntityActionAttackEntity deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int targetEntityId = buffer.getInt();
		return new Deprecated_EntityActionAttackEntity(targetEntityId);
	}


	private final int _targetEntityId;

	@Deprecated
	public Deprecated_EntityActionAttackEntity(int targetEntityId)
	{
		// Note that there is no entity 0 (positive are players, negatives are creatures).
		Assert.assertTrue(0 != targetEntityId);
		
		_targetEntityId = targetEntityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Not used.
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
		buffer.putInt(_targetEntityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Target entity may have moved so don't save this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Attack entity " + _targetEntityId;
	}
}
