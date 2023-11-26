package com.jeffdisher.october.logic;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A test of IEntityChange:  This change attempts to add the given items to our inventory, but only passes if we don't
 * currently have any, failing if they won't fit or we already had some.
 */
public class EntityChangeReceiveItem implements IEntityChange
{
	private final int _entityId;
	private final Item _itemType;
	private final int _itemCount;

	public EntityChangeReceiveItem(int entityId, Item itemType, int itemCount)
	{
		_entityId = entityId;
		_itemType = itemType;
		_itemCount = itemCount;
	}

	@Override
	public int getTargetId()
	{
		return _entityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		return _common(newEntity);
	}

	@Override
	public IEntityChange applyChangeReversible(TickProcessingContext context, MutableEntity newEntity)
	{
		IEntityChange reverse = null;
		if (_common(newEntity))
		{
			// We can apply a bit of a hack here since the reverse change doesn't apply to other entities, just us.
			// This means we can "send" to ourselves, since that will update our state correctly, which is all the reverse does.
			// (this is why the receive has all-or-nothing semantics)
			reverse = new EntityChangeSendItem(_entityId, _entityId, _itemType);
		}
		return reverse;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// We don't merge these.
		return false;
	}


	private boolean _common(MutableEntity newEntity)
	{
		boolean didApply = false;
		// First, check if this will fit.
		Inventory oldInventory = newEntity.newInventory;
		int remaining = oldInventory.maxEncumbrance - oldInventory.currentEncumbrance;
		int required = _itemType.encumbrance() * _itemCount;
		if (remaining >= required)
		{
			// This will fit so see if there is an empty slot (we don't add to an existing to simplify the test).
			if (!newEntity.newInventory.items.containsKey(_itemType))
			{
				Map<Item, Items> newItems = new HashMap<>(newEntity.newInventory.items);
				Items toAdd = new Items(_itemType, _itemCount);
				newItems.put(_itemType, toAdd);
				newEntity.newInventory = new Inventory(oldInventory.maxEncumbrance, newItems, oldInventory.currentEncumbrance + required);
				didApply = true;
			}
		}
		return didApply;
	}
}
