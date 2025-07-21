package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionSelectItem implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SELECT_ITEM;

	public static Deprecated_EntityActionSelectItem deserializeFromBuffer(ByteBuffer buffer)
	{
		int inventoryId = buffer.getInt();
		return new Deprecated_EntityActionSelectItem(inventoryId);
	}


	private final int _inventoryId;

	@Deprecated
	public Deprecated_EntityActionSelectItem(int inventoryId)
	{
		_inventoryId = inventoryId;
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
		buffer.putInt(_inventoryId);
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
		return "Select item in active hotbar slot: " + _inventoryId;
	}
}
