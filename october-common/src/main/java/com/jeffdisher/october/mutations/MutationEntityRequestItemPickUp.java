package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A mutation from an entity to request that items from an inventory be moved to its inventory.
 * This is a multi-step process since both the entity and the block need to change and the request needs to start from
 * an entity:
 * -MutationEntityRequestItemPickUp - just checks for available inventory space and starts the process
 * -MutationBlockExtractItems - run against the block to extract the items
 * -MutationEntityStoreToInventory - run against the entity to store the extracted items
 */
public class MutationEntityRequestItemPickUp implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.ITEMS_REQUEST_PULL;

	public static MutationEntityRequestItemPickUp deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		int blockInventoryKey = buffer.getInt();
		int countRequested = buffer.getInt();
		byte inventoryAspect = buffer.get();
		return new MutationEntityRequestItemPickUp(blockLocation, blockInventoryKey, countRequested, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _blockInventoryKey;
	private final int _countRequested;
	private final byte _inventoryAspect;

	public MutationEntityRequestItemPickUp(AbsoluteLocation blockLocation, int blockInventoryKey, int countRequested, byte inventoryAspect)
	{
		_blockLocation = blockLocation;
		_blockInventoryKey = blockInventoryKey;
		_countRequested = countRequested;
		_inventoryAspect = inventoryAspect;
	}

	@Override
	public long getTimeCostMillis()
	{
		// We will currently assume that accepting items is instantaneous.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// See what this is, in the block's inventory, to make sure that we aren't trying to over-fetch.
		BlockProxy target = context.previousBlockLookUp.apply(_blockLocation);
		Inventory inv = _getInventory(target);
		
		boolean didApply = false;
		if (null != inv)
		{
			Items stack = inv.getStackForKey(_blockInventoryKey);
			NonStackableItem nonStack = inv.getNonStackableForKey(_blockInventoryKey);
			Item type;
			if (null != stack)
			{
				type = stack.type();
			}
			else if (null != nonStack)
			{
				// In this case, we can only request one.
				if (1 == _countRequested)
				{
					type = nonStack.type();
				}
				else
				{
					// This is inconsistent so just fail.
					type = null;
				}
			}
			else
			{
				// This can happen if the inventory has changed.
				type = null;
			}
			if (null != type)
			{
				// We will still try a best-efforts request if the inventory has changed.
				IMutableInventory mutableInventory = newEntity.accessMutableInventory();
				int maxToFetch = mutableInventory.maxVacancyForItem(type);
				int toFetch = Math.min(maxToFetch, _countRequested);
				
				if (toFetch > 0)
				{
					// Request that the items be extracted and sent back to us.
					context.mutationSink.next(new MutationBlockExtractItems(_blockLocation, _blockInventoryKey, toFetch, _inventoryAspect, newEntity.getId()));
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
		buffer.putInt(_blockInventoryKey);
		buffer.putInt(_countRequested);
		buffer.put(_inventoryAspect);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Block reference.
		return false;
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
			FuelState fuel = block.getFuel();
			inv = (null != fuel)
					? fuel.fuelInventory()
					: null
			;
			break;
		default:
			throw Assert.unreachable();
		}
		return inv;
	}
}
