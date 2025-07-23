package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is almost the same as MutationBlockExtractItems but is specifically intended to be used when requesting that
 * items be passed directly from one block to another.  This is typically created in HopperHelpers.
 * If the target block has the capacity for the requested item, it will be sent to it using MutationBlockStoreItems.
 */
public class MutationBlockPushToBlock implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.PUSH_ITEMS_TO_BLOCK;

	public static MutationBlockPushToBlock deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		int blockInventoryKey = buffer.getInt();
		int countRequested = buffer.getInt();
		byte inventoryAspect = buffer.get();
		AbsoluteLocation receiverBlockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockPushToBlock(location, blockInventoryKey, countRequested, inventoryAspect, receiverBlockLocation);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _blockInventoryKey;
	private final int _countRequested;
	private final byte _inventoryAspect;
	private final AbsoluteLocation _receiverBlockLocation;

	public MutationBlockPushToBlock(AbsoluteLocation blockLocation, int blockInventoryKey, int countRequested, byte inventoryAspect, AbsoluteLocation receiverBlockLocation)
	{
		_blockLocation = blockLocation;
		_blockInventoryKey = blockInventoryKey;
		_countRequested = countRequested;
		_inventoryAspect = inventoryAspect;
		_receiverBlockLocation = receiverBlockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		Inventory existing = _getInventory(newBlock);
		if (null != existing)
		{
			// We will still try a best-efforts request if the inventory has changed.
			Items stack = existing.getStackForKey(_blockInventoryKey);
			NonStackableItem nonStack = existing.getNonStackableForKey(_blockInventoryKey);
			Item requestedType;
			int maxAvailable = 0;
			if (null != stack)
			{
				requestedType = stack.type();
				maxAvailable = stack.count();
			}
			else if (null != nonStack)
			{
				// In this case, we can only request one.
				if (1 == _countRequested)
				{
					requestedType = nonStack.type();
					maxAvailable = 1;
				}
				else
				{
					// This is inconsistent so just fail.
					requestedType = null;
				}
			}
			else
			{
				// This can happen if the inventory has changed.
				requestedType = null;
			}
			
			
			int toFetch = Math.min(maxAvailable, _countRequested);
			if (toFetch > 0)
			{
				// Make sure that the target block has the correct inventory type and capacity.
				BlockProxy targetProxy = context.previousBlockLookUp.apply(_receiverBlockLocation);
				boolean canTransfer = false;
				if (null != targetProxy)
				{
					Inventory targetInventory;
					if (_inventoryAspect == Inventory.INVENTORY_ASPECT_FUEL)
					{
						FuelState fuel = targetProxy.getFuel();
						targetInventory = (null != fuel)
								? fuel.fuelInventory()
								: null
						;
					}
					else
					{
						targetInventory = targetProxy.getInventory();
					}
					if (null != targetInventory)
					{
						Environment env = Environment.getShared();
						int itemSize = env.encumbrance.getEncumbrance(requestedType);
						int targetSpace = targetInventory.maxEncumbrance - targetInventory.currentEncumbrance;
						canTransfer = (itemSize <= targetSpace);
					}
				}
				if (canTransfer)
				{
					MutableInventory mutable = new MutableInventory(existing);
					Items stackToSend;
					NonStackableItem nonStackToSend;
					if (null != stack)
					{
						mutable.removeStackableItems(requestedType, toFetch);
						stackToSend = new Items(requestedType, toFetch);
						nonStackToSend = null;
					}
					else
					{
						mutable.removeNonStackableItems(_blockInventoryKey);
						stackToSend = null;
						nonStackToSend = nonStack;
					}
					_putInventory(newBlock, mutable.freeze());
					// This is only used to push directly to normal inventory.
					MutationBlockStoreItems store = new MutationBlockStoreItems(_receiverBlockLocation, stackToSend, nonStackToSend, Inventory.INVENTORY_ASPECT_INVENTORY);
					context.mutationSink.next(store);
					didApply = true;
				}
			}
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
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
		CodecHelpers.writeAbsoluteLocation(buffer, _receiverBlockLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This depends on a return entity ID.
		return false;
	}


	private Inventory _getInventory(IMutableBlockProxy block)
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

	private void _putInventory(IMutableBlockProxy block, Inventory inv)
	{
		switch (_inventoryAspect)
		{
		case Inventory.INVENTORY_ASPECT_INVENTORY:
			block.setInventory(inv);
			break;
		case Inventory.INVENTORY_ASPECT_FUEL:
			FuelState fuel = block.getFuel();
			block.setFuel(new FuelState(fuel.millisFuelled(), fuel.currentFuel(), inv));
			break;
		default:
			throw Assert.unreachable();
		}
	}
}
