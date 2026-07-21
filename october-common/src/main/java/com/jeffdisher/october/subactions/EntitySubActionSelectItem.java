package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Selects the given item type if it exists in the entity's inventory.  Fails if it is already selected or is not in the
 * inventory.
 */
public class EntitySubActionSelectItem implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.SELECT_ITEM;

	public static EntitySubActionSelectItem deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int inventoryId = buffer.getInt();
		return new EntitySubActionSelectItem(inventoryId);
	}


	private final int _inventoryId;

	public EntitySubActionSelectItem(int inventoryId)
	{
		_inventoryId = inventoryId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		boolean didApply = false;
		
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		ItemSlot slot = mutableInventory.getSlotForKey(_inventoryId);
		if ((_inventoryId != newEntity.getSelectedKey())
				&& ((Entity.NO_SELECTION == _inventoryId) || (null != slot)))
		{
			// Remove from any other slots and select in the current slot.
			newEntity.clearHotBarWithKey(_inventoryId);
			newEntity.setSelectedKey(_inventoryId);
			newEntity.setCurrentChargeMillis(0);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
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
