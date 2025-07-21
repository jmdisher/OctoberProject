package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Selects the given item type if it exists in the entity's inventory.  Fails if it is already selected or is not in the
 * inventory.
 */
public class MutationEntitySelectItem implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.SELECT_ITEM;

	public static MutationEntitySelectItem deserializeFromBuffer(ByteBuffer buffer)
	{
		int inventoryId = buffer.getInt();
		return new MutationEntitySelectItem(inventoryId);
	}


	private final int _inventoryId;

	public MutationEntitySelectItem(int inventoryId)
	{
		_inventoryId = inventoryId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		boolean didApply = false;
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		Items stack = mutableInventory.getStackForKey(_inventoryId);
		NonStackableItem nonStack = mutableInventory.getNonStackableForKey(_inventoryId);
		boolean isValidId = ((null != stack) || (null != nonStack));
		if ((_inventoryId != newEntity.getSelectedKey())
				&& ((0 == _inventoryId) || isValidId))
		{
			// Remove from any other slots and select in the current slot.
			newEntity.clearHotBarWithKey(_inventoryId);
			newEntity.setSelectedKey(_inventoryId);
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
