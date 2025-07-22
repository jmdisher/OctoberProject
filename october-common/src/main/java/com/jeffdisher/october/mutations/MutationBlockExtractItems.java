package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
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
 * Called by MutationEntityRequestItemPickUp to request that items be extracted from this inventory and sent back to the
 * calling entity.
 */
public class MutationBlockExtractItems implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.EXTRACT_ITEMS;

	public static MutationBlockExtractItems deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		int blockInventoryKey = buffer.getInt();
		int countRequested = buffer.getInt();
		byte inventoryAspect = buffer.get();
		int returnEntity = buffer.getInt();
		return new MutationBlockExtractItems(location, blockInventoryKey, countRequested, inventoryAspect, returnEntity);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _blockInventoryKey;
	private final int _countRequested;
	private final byte _inventoryAspect;
	private final int _returnEntityId;

	public MutationBlockExtractItems(AbsoluteLocation blockLocation, int blockInventoryKey, int countRequested, byte inventoryAspect, int returnEntityId)
	{
		_blockLocation = blockLocation;
		_blockInventoryKey = blockInventoryKey;
		_countRequested = countRequested;
		_inventoryAspect = inventoryAspect;
		_returnEntityId = returnEntityId;
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
				context.newChangeSink.next(_returnEntityId, new MutationEntityStoreToInventory(stackToSend, nonStackToSend));
				didApply = true;
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
		buffer.putInt(_returnEntityId);
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
