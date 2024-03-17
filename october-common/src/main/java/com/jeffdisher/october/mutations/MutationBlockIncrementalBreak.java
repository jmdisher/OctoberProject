package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to the block.  If this results in the damage value exceeding the maximum for this block type, the
 * block will be replaced by air and dropped as an item in the inventory of the air block.
 */
public class MutationBlockIncrementalBreak implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INCREMENTAL_BREAK_BLOCK;

	public static MutationBlockIncrementalBreak deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		short damageToApply = buffer.getShort();
		return new MutationBlockIncrementalBreak(location, damageToApply);
	}


	private final AbsoluteLocation _location;
	private final short _damageToApply;

	public MutationBlockIncrementalBreak(AbsoluteLocation location, short damageToApply)
	{
		_location = location;
		_damageToApply = damageToApply;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		
		// We want to see if this is a kind of block which can be broken.
		Item block = newBlock.getBlock().asItem();
		if (DamageAspect.UNBREAKABLE != DamageAspect.getToughness(block))
		{
			// Apply the damage.
			short damage = (short)(newBlock.getDamage() + _damageToApply);
			
			// See if this is broken (note that this could overflow.
			if ((damage >= DamageAspect.getToughness(block)) || (damage < 0))
			{
				// The block is broken so replace it with air and place the block in the inventory.
				Inventory oldInventory = newBlock.getInventory();
				FuelState oldFuel = newBlock.getFuel();
				newBlock.setBlockAndClear(BlockAspect.getBlock(ItemRegistry.AIR));
				
				// If the inventory fits, move it over to the new block.
				// TODO:  Handle the case where the inventory can't fit when we have cases where it might be too big.
				Assert.assertTrue((null == oldInventory) || (oldInventory.currentEncumbrance <= InventoryAspect.CAPACITY_AIR));
				
				// We want to drop this block in the inventory, if it fits.
				// This will create the new inventory since setting the item clears.
				Inventory newInventory = newBlock.getInventory();
				MutableInventory mutable = new MutableInventory(newInventory);
				_combineInventory(mutable, oldInventory);
				if (null != oldFuel)
				{
					_combineInventory(mutable, oldFuel.fuelInventory());
				}
				mutable.addItemsBestEfforts(block, 1);
				
				// Note that we need to handle the special-case where the block below this one is also empty and we should actually drop all the items.
				AbsoluteLocation belowLocation = _location.getRelative(0, 0, -1);
				BlockProxy below = context.previousBlockLookUp.apply(belowLocation);
				if ((null != below) && below.getBlock().canBeReplaced())
				{
					// We want to drop this inventory into the below block.
					for (Items items : mutable.freeze().items.values())
					{
						context.newMutationSink.accept(new MutationBlockStoreItems(belowLocation, items, Inventory.INVENTORY_ASPECT_INVENTORY));
					}
				}
				else
				{
					// We are just storing this into the block.
					newBlock.setInventory(mutable.freeze());
				}
			}
			else
			{
				// The block still exists so just update the damage.
				newBlock.setDamage(damage);
			}
			didApply = true;
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		buffer.putShort(_damageToApply);
	}


	private void _combineInventory(MutableInventory mutable, Inventory oldInventory)
	{
		if (null != oldInventory)
		{
			for (Items items : oldInventory.items.values())
			{
				mutable.addAllItems(items.type(), items.count());
			}
		}
	}
}
