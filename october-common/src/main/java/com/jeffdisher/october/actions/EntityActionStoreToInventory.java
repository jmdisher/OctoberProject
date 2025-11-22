package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is the final step in the process started by MutationEntityRequestItemPickUp.  Called more directly by
 * MutationBlockExtractItems.
 * Also called by MutationBlockIncrementalBreak in order to store new blocks to the entity's inventory.
 * It is also used by PassiveActionPickUp.
 * If the inventory can't fit all the items, those which overflow are dropped onto the ground where the entity is with
 * MutationBlockStoreItems.
 */
public class EntityActionStoreToInventory implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.ITEMS_STORE_TO_INVENTORY;

	public static EntityActionStoreToInventory deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		Items stack = CodecHelpers.readItems(buffer);
		NonStackableItem nonStack = CodecHelpers.readNonStackableItem(context);
		return new EntityActionStoreToInventory(stack, nonStack);
	}


	private final Items _stack;
	private final NonStackableItem _nonStack;

	public EntityActionStoreToInventory(Items stack, NonStackableItem nonStack)
	{
		// Precisely one of these must be non-null.
		Assert.assertTrue((null != stack) != (null != nonStack));
		
		_stack = stack;
		_nonStack = nonStack;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// We will still try a best-efforts request if the inventory has changed (but drop anything else).
		int itemsToStore;
		int stored;
		int itemKeyToSelect = 0;
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		if (null != _stack)
		{
			Item type = _stack.type();
			itemsToStore = _stack.count();
			stored = mutableInventory.addItemsBestEfforts(type, itemsToStore);
			if (stored > 0)
			{
				itemKeyToSelect = mutableInventory.getIdOfStackableType(type);
			}
		}
		else
		{
			itemsToStore = 1;
			boolean didStore = mutableInventory.addNonStackableBestEfforts(_nonStack);
			stored = didStore ? 1 : 0;
			if (didStore)
			{
				itemKeyToSelect = mutableInventory.getIdOfNonStackableInstance(_nonStack);
			}
		}
		
		// Just as a "nice to have" behaviour, we will select this item if we have nothing selected and we didn't have any of this item.
		if ((0 != itemKeyToSelect) && (Entity.NO_SELECTION == newEntity.getSelectedKey()))
		{
			newEntity.clearHotBarWithKey(itemKeyToSelect);
			newEntity.setSelectedKey(itemKeyToSelect);
		}
		
		// If there are items left over, drop them on the ground as passives.
		if (itemsToStore > stored)
		{
			ItemSlot slot;
			if (null != _stack)
			{
				int itemsToDrop = itemsToStore - stored;
				Items stack = new Items(_stack.type(), itemsToDrop);
				slot = ItemSlot.fromStack(stack);
			}
			else
			{
				slot = ItemSlot.fromNonStack(_nonStack);
			}
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, newEntity.getLocation(), new EntityLocation(0.0f, 0.0f, 0.0f), slot);
		}
		
		// Since we did _something_, this is always true.
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeItems(buffer, _stack);
		CodecHelpers.writeNonStackableItem(buffer, _nonStack);
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
		return "Store to entity inventory " + ((null != _stack) ? _stack : _nonStack);
	}
}
