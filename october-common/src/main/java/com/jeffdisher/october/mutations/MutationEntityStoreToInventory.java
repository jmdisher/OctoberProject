package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is the final step in the process started by MutationEntityRequestItemPickUp.  Called more directly by
 * MutationBlockExtractItems.
 * Also called by MutationBlockIncrementalBreak in order to store new blocks to the entity's inventory.
 * If the inventory can't fit all the items, those which overflow are dropped onto the ground where the entity is with
 * MutationBlockStoreItems.
 */
public class MutationEntityStoreToInventory implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.ITEMS_STORE_TO_INVENTORY;

	public static MutationEntityStoreToInventory deserializeFromBuffer(ByteBuffer buffer)
	{
		Items stack = CodecHelpers.readItems(buffer);
		NonStackableItem nonStack = CodecHelpers.readNonStackableItem(buffer);
		return new MutationEntityStoreToInventory(stack, nonStack);
	}


	private final Items _stack;
	private final NonStackableItem _nonStack;

	public MutationEntityStoreToInventory(Items stack, NonStackableItem nonStack)
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
		Item typeToTrySelect = null;
		int itemsToStore;
		int stored;
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		if (null != _stack)
		{
			Item type = _stack.type();
			if (0 == mutableInventory.getCount(type))
			{
				typeToTrySelect = type;
			}
			itemsToStore = _stack.count();
			stored = mutableInventory.addItemsBestEfforts(type, itemsToStore);
		}
		else
		{
			itemsToStore = 1;
			boolean didStore = mutableInventory.addNonStackableBestEfforts(_nonStack);
			stored = didStore ? 1 : 0;
		}
		
		if (stored > 0)
		{
			// Just as a "nice to have" behaviour, we will select this item if we have nothing selected and we didn't have any of this item.
			if ((Entity.NO_SELECTION == newEntity.getSelectedKey()) && (null != typeToTrySelect))
			{
				newEntity.setSelectedKey(mutableInventory.getIdOfStackableType(typeToTrySelect));
			}
		}
		
		// If there are items left over, drop them on the ground.
		if (itemsToStore > stored)
		{
			Items stack = null;
			if (null != _stack)
			{
				int itemsToDrop = itemsToStore - stored;
				stack = new Items(_stack.type(), itemsToDrop);
			}
			MutationBlockStoreItems drop = new MutationBlockStoreItems(newEntity.getLocation().getBlockLocation(), stack, _nonStack, Inventory.INVENTORY_ASPECT_INVENTORY);
			context.mutationSink.next(drop);
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
