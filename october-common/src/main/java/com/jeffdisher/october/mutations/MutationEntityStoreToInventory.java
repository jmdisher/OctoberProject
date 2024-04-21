package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
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
public class MutationEntityStoreToInventory implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.ITEMS_STORE_TO_INVENTORY;

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
	public long getTimeCostMillis()
	{
		// We will currently assume that accepting items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We will still try a best-efforts request if the inventory has changed (but drop anything else).
		Item typeToTrySelect = null;
		int itemsToStore;
		int stored;
		if (null != _stack)
		{
			Item type = _stack.type();
			if (0 == newEntity.newInventory.getCount(type))
			{
				typeToTrySelect = type;
			}
			itemsToStore = _stack.count();
			stored = newEntity.newInventory.addItemsBestEfforts(type, itemsToStore);
		}
		else
		{
			itemsToStore = 1;
			boolean didStore = newEntity.newInventory.addNonStackableBestEfforts(_nonStack);
			stored = didStore ? 1 : 0;
		}
		
		if (stored > 0)
		{
			// Just as a "nice to have" behaviour, we will select this item if we have nothing selected and we didn't have any of this item.
			if ((Entity.NO_SELECTION == newEntity.newSelectedItemKey) && (null != typeToTrySelect))
			{
				newEntity.newSelectedItemKey = newEntity.newInventory.getIdOfStackableType(typeToTrySelect);
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
			MutationBlockStoreItems drop = new MutationBlockStoreItems(newEntity.newLocation.getBlockLocation(), stack, _nonStack, Inventory.INVENTORY_ASPECT_INVENTORY);
			context.mutationSink.next(drop);
		}
		
		// Since we did _something_, this is always true.
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeItems(buffer, _stack);
		CodecHelpers.writeNonStackableItem(buffer, _nonStack);
	}
}
