package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
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
		int localInventoryId = buffer.getInt();
		Assert.assertTrue(localInventoryId > 0);
		int count = buffer.getInt();
		Assert.assertTrue(count > 0);
		byte inventoryAspect = buffer.get();
		return new MutationEntityPushItems(blockLocation, localInventoryId, count, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _localInventoryId;
	private final int _count;
	private final byte _inventoryAspect;

	public MutationEntityPushItems(AbsoluteLocation blockLocation, int localInventoryId, int count, byte inventoryAspect)
	{
		Assert.assertTrue(localInventoryId > 0);
		Assert.assertTrue(count > 0);
		
		_blockLocation = blockLocation;
		_localInventoryId = localInventoryId;
		_count = count;
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
		
		// Make sure that we actually have this much of the referenced item in our inventory.
		Items stackable = newEntity.newInventory.getStackForKey(_localInventoryId);
		NonStackableItem nonStackable = newEntity.newInventory.getNonStackableForKey(_localInventoryId);
		// We should see precisely one of these.
		Assert.assertTrue((null != stackable) != (null != nonStackable));
		
		// We want to make sure that this is a block which can accept items (currently just air).
		BlockProxy block = context.previousBlockLookUp.apply(_blockLocation);
		boolean canTransfer;
		Inventory inv;
		Item type;
		if (null != stackable)
		{
			canTransfer = (stackable.count() >= _count);
			inv = _getInventory(block, stackable.type());
			type = stackable.type();
		}
		else
		{
			canTransfer = true;
			inv = _getInventory(block, nonStackable.type());
			// In this case, it MUST be only 1.
			Assert.assertTrue(1 == _count);
			type = nonStackable.type();
		}
		if (canTransfer && (null != inv))
		{
			// See if there is space in the inventory.
			MutableInventory checker = new MutableInventory(inv);
			
			int capacity = checker.maxVacancyForItem(type);
			int toDrop = Math.min(capacity, _count);
			if (toDrop > 0)
			{
				// We will proceed to remove the items from our inventory and pass them to the block.
				Items stackToMove;
				if (null != stackable)
				{
					newEntity.newInventory.removeStackableItems(type, toDrop);
					stackToMove = new Items(type, toDrop);
				}
				else
				{
					newEntity.newInventory.removeNonStackableItems(_localInventoryId);
					stackToMove = null;
				}
				context.mutationSink.next(new MutationBlockStoreItems(_blockLocation, stackToMove, nonStackable, _inventoryAspect));
				
				// We want to deselect this if it was selected.
				boolean shouldClear = (null != nonStackable) || (0 == newEntity.newInventory.getCount(type));
				if ((_localInventoryId == newEntity.newSelectedItemKey)
						&& shouldClear
				)
				{
					newEntity.newSelectedItemKey = Entity.NO_SELECTION;
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
		buffer.putInt(_localInventoryId);
		buffer.putInt(_count);
		buffer.put(_inventoryAspect);
	}


	private Inventory _getInventory(BlockProxy block, Item type)
	{
		Environment env = Environment.getShared();
		Inventory inv;
		switch (_inventoryAspect)
		{
		case Inventory.INVENTORY_ASPECT_INVENTORY:
			inv = block.getInventory();
			break;
		case Inventory.INVENTORY_ASPECT_FUEL:
			inv = env.fuel.hasFuelInventoryForType(block.getBlock(), type)
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
