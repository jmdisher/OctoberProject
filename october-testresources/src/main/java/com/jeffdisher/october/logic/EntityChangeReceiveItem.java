package com.jeffdisher.october.logic;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A test of IEntityChange:  This change attempts to add the given items to our inventory, but only passes if we don't
 * currently have any, failing if they won't fit or we already had some.
 */
public class EntityChangeReceiveItem implements IMutationEntity
{
	private final Item _itemType;
	private final int _itemCount;

	public EntityChangeReceiveItem(Item itemType, int itemCount)
	{
		_itemType = itemType;
		_itemCount = itemCount;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		return _common(newEntity);
	}


	private boolean _common(MutableEntity newEntity)
	{
		boolean didApply = false;
		MutableInventory inventory = newEntity.newInventory;
		
		// Make sure that we don't already have some.
		if (0 == inventory.getCount(_itemType))
		{
			// Try to add them.
			didApply = inventory.addAllItems(_itemType, _itemCount);
		}
		return didApply;
	}
}
