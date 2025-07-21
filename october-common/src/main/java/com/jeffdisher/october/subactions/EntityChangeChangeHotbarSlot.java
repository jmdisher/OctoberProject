package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Changes the currently-selected hotbar slot for this entity.
 */
public class EntityChangeChangeHotbarSlot implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.CHANGE_HOTBAR_SLOT;

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
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		return newEntity.changeHotbarIndex(_index);
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_index);
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
		return "Select hotbar index " + _index;
	}
}
