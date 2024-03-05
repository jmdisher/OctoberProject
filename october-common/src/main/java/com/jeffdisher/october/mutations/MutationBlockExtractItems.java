package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Called by MutationEntityRequestItemPickUp to request that items be extracted from this inventory and sent back to the
 * calling entity.
 */
public class MutationBlockExtractItems implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.EXTRACT_ITEMS;

	public static MutationBlockExtractItems deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Items requested = CodecHelpers.readItems(buffer);
		byte inventoryAspect = buffer.get();
		int returnEntity = buffer.getInt();
		return new MutationBlockExtractItems(location, requested, inventoryAspect, returnEntity);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _requested;
	private final byte _inventoryAspect;
	private final int _returnEntityId;

	public MutationBlockExtractItems(AbsoluteLocation blockLocation, Items requested, byte inventoryAspect, int returnEntityId)
	{
		_blockLocation = blockLocation;
		_requested = requested;
		_returnEntityId = returnEntityId;
		_inventoryAspect = inventoryAspect;
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
			MutableInventory mutable = new MutableInventory(existing);
			Item requestedType = _requested.type();
			int maxAvailable = mutable.getCount(requestedType);
			int toFetch = Math.min(maxAvailable, _requested.count());
			if (toFetch > 0)
			{
				mutable.removeItems(requestedType, toFetch);
				_putInventory(newBlock, mutable.freeze());
				context.newChangeSink.accept(_returnEntityId, new MutationEntityStoreToInventory(new Items(requestedType, toFetch)));
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
		CodecHelpers.writeItems(buffer, _requested);
		buffer.put(_inventoryAspect);
		buffer.putInt(_returnEntityId);
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
			block.setFuel(new FuelState(fuel.millisFueled(), inv));
			break;
		default:
			throw Assert.unreachable();
		}
	}
}
