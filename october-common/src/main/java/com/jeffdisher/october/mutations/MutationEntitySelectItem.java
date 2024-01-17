package com.jeffdisher.october.mutations;

import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Selects the given item type if it exists in the entity's inventory.  Fails if it is already selected or is not in the
 * inventory.
 */
public class MutationEntitySelectItem implements IMutationEntity
{
	private final Item _itemType;

	public MutationEntitySelectItem(Item itemType)
	{
		_itemType = itemType;
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
		if ((_itemType != newEntity.newSelectedItem) && (newEntity.newInventory.getCount(_itemType) > 0))
		{
			newEntity.newSelectedItem = _itemType;
			didApply = true;
		}
		return didApply;
	}
}
