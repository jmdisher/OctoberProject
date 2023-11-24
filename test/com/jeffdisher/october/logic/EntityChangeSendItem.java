package com.jeffdisher.october.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;


/**
 * A test of IEntityChange:  This change extracts all items of a given type from the entity's inventory and, if it is
 * more than 0, creates a new change to pass it to another entity.
 */
public class EntityChangeSendItem implements IEntityChange
{
	private final int _sourceId;
	private final int _targetId;
	private final Item _itemType;

	public EntityChangeSendItem(int sourceId, int targetId, Item itemType)
	{
		_sourceId = sourceId;
		_targetId = targetId;
		_itemType = itemType;
	}

	@Override
	public int getTargetId()
	{
		// We run against the source so that we can modify their inventory to extract the item.
		return _sourceId;
	}

	@Override
	public boolean applyChange(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
	{
		int foundCount = _common(newEntity, newChangeSink);
		return foundCount > 0;
	}

	@Override
	public IEntityChange applyChangeReversible(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
	{
		IEntityChange reverse = null;
		int foundCount = _common(newEntity, newChangeSink);
		if (foundCount > 0)
		{
			// This worked so we will need a reverse - just receive the items, ourselves.
			reverse = new EntityChangeReceiveItem(_sourceId, _itemType, foundCount);
		}
		return reverse;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// We don't merge these.
		return false;
	}


	private int _common(MutableEntity newEntity, Consumer<IEntityChange> newChangeSink)
	{
		// Extract all items of this type from the entity, failing the mutation if there aren't any.
		List<Items> newItems = new ArrayList<>();
		int foundCount = 0;
		Inventory oldInventory = newEntity.newInventory;
		for (Items items : oldInventory.items)
		{
			if (_itemType == items.type())
			{
				foundCount = items.count();
			}
			else
			{
				newItems.add(items);
			}
		}
		
		if (foundCount > 0)
		{
			// Update the inventory.
			int reducedEncumbrance = _itemType.encumbrance() * foundCount;
			newEntity.newInventory = new Inventory(oldInventory.maxEncumbrance, newItems, oldInventory.currentEncumbrance - reducedEncumbrance);
			// Send this to the other entity.
			newChangeSink.accept(new EntityChangeReceiveItem(_targetId, _itemType, foundCount));
		}
		return foundCount;
	}
}
