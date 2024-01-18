package com.jeffdisher.october.mutations;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is the final step in the process started by MutationEntityRequestItemPickUp.  Called more directly by
 * MutationBlockExtractItems.
 * If the inventory somehow changed to not be able to fit this, then the items are destroyed.
 */
public class MutationEntityStoreToInventory implements IMutationEntity
{
	private final Items _items;

	public MutationEntityStoreToInventory(Items items)
	{
		_items = items;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will currently assume that accepting items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We will still try a best-efforts request if the inventory has changed (but drop anything else).
		int stored = newEntity.newInventory.addItemsBestEfforts(_items.type(), _items.count());
		return (stored > 0);
	}
}
