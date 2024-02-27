package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Asks the entity to move items from its inventory into a block.  Calls MutationBlockStoreItems to update the block
 * inventory.
 * Note that races here can result in items being destroyed (if the same block is over-filled in one tick).
 */
public class MutationEntityPushItems implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.ITEMS_REQUEST_PUSH;

	public static MutationEntityPushItems deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		Items offered = CodecHelpers.readItems(buffer);
		return new MutationEntityPushItems(blockLocation, offered);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _offered;

	public MutationEntityPushItems(AbsoluteLocation blockLocation, Items offered)
	{
		Assert.assertTrue(offered.count() > 0);
		
		_blockLocation = blockLocation;
		_offered = offered;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will currently assume that dropping items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		
		// First off, we want to make sure that this is a block which can accept items (currently just air).
		BlockProxy block = context.previousBlockLookUp.apply(_blockLocation);
		Inventory inv = block.getInventory();
		if (null != inv)
		{
			// See if there is space in the inventory.
			MutableInventory checker = new MutableInventory(inv);
			Item offeredType = _offered.type();
			int capacity = checker.maxVacancyForItem(offeredType);
			int toDrop = Math.min(capacity, _offered.count());
			if (toDrop > 0)
			{
				// We will proceed to remove the items from our inventory and pass them to the block.
				newEntity.newInventory.removeItems(offeredType, toDrop);
				context.newMutationSink.accept(new MutationBlockStoreItems(_blockLocation, new Items(offeredType, toDrop)));
				
				// We want to deselect this if it was selected.
				if ((offeredType == newEntity.newSelectedItem) && (0 == newEntity.newInventory.getCount(offeredType)))
				{
					newEntity.newSelectedItem = null;
				}
				
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		CodecHelpers.writeItems(buffer, _offered);
	}
}
