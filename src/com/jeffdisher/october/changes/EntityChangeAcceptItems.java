package com.jeffdisher.october.changes;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Stores the given items into the entity's inventory.
 */
public class EntityChangeAcceptItems implements IEntityChange
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
		int requiredEncumbrance = _items.type().encumbrance() * _items.count();
		Inventory original = newEntity.newInventory;
		
		boolean didApply = false;
		if ((original.currentEncumbrance + requiredEncumbrance) <= original.maxEncumbrance)
		{
			Items existing = original.items.get(_items.type());
			Items updated;
			if (null != existing)
			{
				updated = new Items(_items.type(), existing.count() + _items.count());
			}
			else
			{
				updated = _items;
			}
			Map<Item, Items> newMap = new HashMap<>(original.items);
			newMap.put(updated.type(), updated);
			Inventory finished = new Inventory(original.maxEncumbrance, newMap, original.currentEncumbrance + requiredEncumbrance);
			newEntity.newInventory = finished;
			didApply = true;
		}
		return didApply;
	}
}
