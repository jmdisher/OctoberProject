package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Selects the given item type if it exists in the entity's inventory.  Fails if it is already selected or is not in the
 * inventory.
 */
public class MutationEntitySelectItem implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.SELECT_ITEM;

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
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		Items stack = newEntity.newInventory.getStackForKey(_inventoryId);
		NonStackableItem nonStack = newEntity.newInventory.getNonStackableForKey(_inventoryId);
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
	public MutationEntityType getType()
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
}
