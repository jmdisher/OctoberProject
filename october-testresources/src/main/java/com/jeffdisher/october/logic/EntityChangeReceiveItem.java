package com.jeffdisher.october.logic;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A test of IEntityChange:  This change attempts to add the given items to our inventory, but only passes if we don't
 * currently have any, failing if they won't fit or we already had some.
 */
public class EntityChangeReceiveItem implements IEntityAction<IMutablePlayerEntity>
{
	private final Item _itemType;
	private final int _itemCount;

	public EntityChangeReceiveItem(Item itemType, int itemCount)
	{
		_itemType = itemType;
		_itemCount = itemCount;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		return _common(newEntity);
	}


	private boolean _common(IMutablePlayerEntity newEntity)
	{
		boolean didApply = false;
		IMutableInventory inventory = newEntity.accessMutableInventory();
		
		// Make sure that we don't already have some.
		if (0 == inventory.getCount(_itemType))
		{
			// Try to add them.
			didApply = inventory.addAllItems(_itemType, _itemCount);
		}
		return didApply;
	}

	@Override
	public EntityActionType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
