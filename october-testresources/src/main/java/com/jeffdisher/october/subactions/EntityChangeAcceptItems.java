package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableSlotManager;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Stores the given items into the entity's inventory.
 */
public class EntityChangeAcceptItems implements IEntitySubAction<IMutablePlayerEntity>
{
	private final Items _items;

	public EntityChangeAcceptItems(Items items)
	{
		_items = items;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		MutableSlotManager slotManager = newEntity.getSlotManager();
		boolean didAdd = slotManager.addStackableItems(_items.type(), _items.count());
		return didAdd;
	}

	@Override
	public EntitySubActionType getType()
	{
		// This is only used in testing (can't come from clients as it has no deserializer).
		return EntitySubActionType.TESTING_ONLY;
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
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Accept items " + _items;
	}
}
