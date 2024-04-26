package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Changes the currently-selected hotbar slot for this entity.
 */
public class EntityChangeChangeHotbarSlot implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.CHANGE_HOTBAR_SLOT;

	public static EntityChangeChangeHotbarSlot deserializeFromBuffer(ByteBuffer buffer)
	{
		int index = buffer.getInt();
		return new EntityChangeChangeHotbarSlot(index);
	}


	private final int _index;

	public EntityChangeChangeHotbarSlot(int index)
	{
		Assert.assertTrue(index >= 0);
		Assert.assertTrue(index < Entity.HOTBAR_SIZE);
		
		_index = index;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		if (newEntity.newHotbarIndex != _index)
		{
			newEntity.newHotbarIndex = _index;
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_index);
	}
}
