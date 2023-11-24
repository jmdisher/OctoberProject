package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;


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
	public boolean applyChange(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
	{
		return _common(newEntity);
	}

	@Override
	public IEntityChange applyChangeReversible(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
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
			List<Items> newItems = new ArrayList<>();
			Items toAdd = new Items(_itemType, _itemCount);
			for (Items items : newEntity.newInventory.items)
			{
				if (_itemType == items.type())
				{
					// There is already a slot with this value, so we can't accept
					toAdd = null;
					break;
				}
				else
				{
					newItems.add(items);
				}
			}
			if (null != toAdd)
			{
				// There are no duplicates so add this.
				newItems.add(toAdd);
				// Write-back the change.
				newEntity.newInventory = new Inventory(oldInventory.maxEncumbrance, newItems, oldInventory.currentEncumbrance + required);
				didApply = true;
			}
		}
		return didApply;
	}
}
