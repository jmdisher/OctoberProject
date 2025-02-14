package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.CraftingBlockSupport;
import com.jeffdisher.october.logic.HopperHelpers;
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
 * Called by MutationEntityPushItems to store items into the inventory in a given block.
 * Note that this mutation can over-fill the inventory so users of this mutation should be sure that this is what they
 * expect.
 */
public class MutationBlockStoreItems implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.STORE_ITEMS;

	public static MutationBlockStoreItems deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Items stackable = CodecHelpers.readItems(buffer);
		NonStackableItem nonStackable = CodecHelpers.readNonStackableItem(buffer);
		byte inventoryAspect = buffer.get();
		return new MutationBlockStoreItems(location, stackable, nonStackable, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final Items _stackable;
	private final NonStackableItem _nonStackable;
	private final byte _inventoryAspect;

	public MutationBlockStoreItems(AbsoluteLocation blockLocation, Items stackable, NonStackableItem nonStackable, byte inventoryAspect)
	{
		// Precisely one of these must be non-null.
		Assert.assertTrue((null != stackable) != (null != nonStackable));
		
		_blockLocation = blockLocation;
		_stackable = stackable;
		_nonStackable = nonStackable;
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
		Environment env = Environment.getShared();
		boolean didApply = false;
		// First, we want to check the special case of trying to store items into an air block above an air block, since we should just shift down, in the case.
		if (Inventory.INVENTORY_ASPECT_INVENTORY == _inventoryAspect)
		{
			if (env.blocks.hasEmptyBlockInventory(newBlock.getBlock()))
			{
				// This block has an empty inventory but we want to drop it down a block, if it has an empty inventory.
				AbsoluteLocation belowLocation = _blockLocation.getRelative(0, 0, -1);
				BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
				if ((null != below) && env.blocks.hasEmptyBlockInventory(below.getBlock()))
				{
					// We want to drop this into the below block.
					context.mutationSink.next(new MutationBlockStoreItems(belowLocation, _stackable, _nonStackable, _inventoryAspect));
					didApply = true;
				}
			}
		}
		
		// If that didn't work, just apply the common case of storing into the block.
		if (!didApply)
		{
			Inventory existing = _getInventory(newBlock);
			if (null != existing)
			{
				MutableInventory inv = new MutableInventory(existing);
				if (null != _stackable)
				{
					inv.addItemsAllowingOverflow(_stackable.type(), _stackable.count());
				}
				else
				{
					Assert.assertTrue(null != _nonStackable);
					inv.addNonStackableAllowingOverflow(_nonStackable);
				}
				_putInventory(newBlock, inv.freeze());
				
				// See if we might need to trigger an automatic crafting operation in this block.
				if (null != CraftingBlockSupport.getValidFuelledCraft(env, newBlock))
				{
					context.mutationSink.next(new MutationBlockFurnaceCraft(_blockLocation));
				}
				didApply = true;
			}
		}
		
		// Handle the case where this might be a hopper.
		if (didApply && HopperHelpers.isHopper(_blockLocation, newBlock))
		{
			newBlock.requestFutureMutation(MutationBlockPeriodic.MILLIS_BETWEEN_HOPPER_CALLS);
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
		CodecHelpers.writeItems(buffer, _stackable);
		CodecHelpers.writeNonStackableItem(buffer, _nonStackable);
		buffer.put(_inventoryAspect);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}


	private Inventory _getInventory(IMutableBlockProxy block)
	{
		Environment env = Environment.getShared();
		Inventory inv;
		switch (_inventoryAspect)
		{
		case Inventory.INVENTORY_ASPECT_INVENTORY:
			inv = block.getInventory();
			break;
		case Inventory.INVENTORY_ASPECT_FUEL:
			Item underlying = (null != _stackable)
				? _stackable.type()
				: _nonStackable.type()
			;
			inv = env.fuel.hasFuelInventoryForType(block.getBlock(), underlying)
				? block.getFuel().fuelInventory()
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
