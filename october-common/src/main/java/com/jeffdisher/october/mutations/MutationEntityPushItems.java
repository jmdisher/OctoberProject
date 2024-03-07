package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.FuelAspect;
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
		byte inventoryAspect = buffer.get();
		return new MutationEntityPushItems(blockLocation, offered, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _offered;
	private final byte _inventoryAspect;

	public MutationEntityPushItems(AbsoluteLocation blockLocation, Items offered, byte inventoryAspect)
	{
		Assert.assertTrue(offered.count() > 0);
		
		_blockLocation = blockLocation;
		_offered = offered;
		_inventoryAspect = inventoryAspect;
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
		Inventory inv = _getInventory(block);
		if (null != inv)
		{
			// See if there is space in the inventory.
			MutableInventory checker = new MutableInventory(inv);
			Item offeredType = _offered.type();
			int capacity = checker.maxVacancyForItem(offeredType);
			int toDrop = Math.min(capacity, _offered.count());
			if (toDrop > 0)
			{
				// Make sure that the inventory actually _has_ these items (this would be an error in the mutation - can happen if a mutation is based on stale state).
				if (newEntity.newInventory.getCount(offeredType) >= toDrop)
				{
					// We will proceed to remove the items from our inventory and pass them to the block.
					newEntity.newInventory.removeItems(offeredType, toDrop);
					context.newMutationSink.accept(new MutationBlockStoreItems(_blockLocation, new Items(offeredType, toDrop), _inventoryAspect));
					
					// We want to deselect this if it was selected.
					if ((offeredType == newEntity.newSelectedItem) && (0 == newEntity.newInventory.getCount(offeredType)))
					{
						newEntity.newSelectedItem = null;
					}
					
					didApply = true;
				}
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
		buffer.put(_inventoryAspect);
	}


	private Inventory _getInventory(BlockProxy block)
	{
		Inventory inv;
		switch (_inventoryAspect)
		{
		case Inventory.INVENTORY_ASPECT_INVENTORY:
			inv = block.getInventory();
			break;
		case Inventory.INVENTORY_ASPECT_FUEL:
			inv = FuelAspect.hasFuelInventoryForType(block.getBlock().asItem(), _offered.type())
				? block.getFuel().fuelInventory()
				: null
			;
			break;
		default:
			throw Assert.unreachable();
		}
		return inv;
	}
}
