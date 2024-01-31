package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is the final step in the process started by MutationEntityRequestItemPickUp.  Called more directly by
 * MutationBlockExtractItems.
 * If the inventory somehow changed to not be able to fit this, then the items are destroyed.
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
		int stored = newEntity.newInventory.addItemsBestEfforts(type, _items.count());
		if (stored > 0)
		{
			// Just as a "nice to have" behaviour, we will select this item if we have nothing selected and we didn't have any of this item.
			if ((null == newEntity.newSelectedItem) && (0 == previousItemCount))
			{
				newEntity.newSelectedItem = type;
			}
		}
		return (stored > 0);
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
