package com.jeffdisher.october.mutations;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Stores the given items into the entity's inventory.
 */
public class EntityChangeAcceptItems implements IMutationEntity
{
	private final Items _items;

	public EntityChangeAcceptItems(Items items)
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
		return newEntity.newInventory.addItems(_items.type(), _items.count());
	}
}
