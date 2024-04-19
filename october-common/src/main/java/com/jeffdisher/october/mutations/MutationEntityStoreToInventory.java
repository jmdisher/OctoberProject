package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


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
		Items items = CodecHelpers.readItems(buffer);
		return new MutationEntityStoreToInventory(items);
	}


	private final Items _items;

	public MutationEntityStoreToInventory(Items items)
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
		// We will still try a best-efforts request if the inventory has changed (but drop anything else).
		Item type = _items.type();
		int previousItemCount = newEntity.newInventory.getCount(type);
		int itemsToStore = _items.count();
		int stored = newEntity.newInventory.addItemsBestEfforts(type, itemsToStore);
		if (stored > 0)
		{
			// Just as a "nice to have" behaviour, we will select this item if we have nothing selected and we didn't have any of this item.
			if ((Entity.NO_SELECTION == newEntity.newSelectedItemKey) && (0 == previousItemCount))
			{
				newEntity.newSelectedItemKey = newEntity.newInventory.getIdOfStackableType(type);
			}
		}
		
		// If there are items left over, drop them on the ground.
		if (itemsToStore > stored)
		{
			int itemsToDrop = itemsToStore - stored;
			MutationBlockStoreItems drop = new MutationBlockStoreItems(newEntity.newLocation.getBlockLocation(), new Items(type, itemsToDrop), Inventory.INVENTORY_ASPECT_INVENTORY);
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
		CodecHelpers.writeItems(buffer, _items);
	}
}
