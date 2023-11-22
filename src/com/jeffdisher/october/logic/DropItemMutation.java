package com.jeffdisher.october.logic;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * The DropItemMutation is specifically made to drop items onto the ground.  This means it will only drop into air
 * blocks.
 * The mutation is purely atomic:  It will either drop all of the items or destroy all of them and fail.
 * Additionally, this mutation cannot be used for groups of items which are larger than what a single air block can
 * hold.
 */
public class DropItemMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final Item _type;
	private final int _count;

	public DropItemMutation(AbsoluteLocation location, Item type, int count)
	{
		// We don't allow creation of this mutation if the items can't possibly fit.
		Assert.assertTrue((type.encumbrance() * count) <= InventoryAspect.CAPACITY_AIR);
		_location = location;
		_type = type;
		_count = count;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		return _common(newBlock);
	}

	@Override
	public IMutation applyMutationReversible(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		IMutation reverseMutation = null;
		if (_common(newBlock))
		{
			// We applied this so create the reverse.
			reverseMutation = new PickUpItemMutation(_location, _type, _count);
		}
		return reverseMutation;
	}


	private boolean _common(MutableBlockProxy newBlock)
	{
		// We can only drop an item into air.
		boolean didApply = false;
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		if (BlockAspect.AIR == oldValue)
		{
			// Get the inventory.
			Inventory inventory = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
			// We lazily construct the inventory.
			if (null == inventory)
			{
				inventory = new Inventory(InventoryAspect.CAPACITY_AIR);
				newBlock.setDataSpecial(AspectRegistry.INVENTORY, inventory);
			}
			// Check if this will fit (note that a fresh inventory will _always_ fit (see assertion in constructor).
			int encumbranceToAdd = _type.encumbrance() * _count;
			if (encumbranceToAdd <= (inventory.maxEncumbrance - inventory.currentEncumbrance))
			{
				// This will fit so see if there is an existing Items to which we can add this or if we need to just insert this.
				Items toInsert = null;
				Iterator<Items> iter = inventory.items.iterator();
				while (iter.hasNext())
				{
					Items items = iter.next();
					if (items.type() == _type)
					{
						iter.remove();
						toInsert = new Items(_type, items.count() + _count);
						break;
					}
				}
				if (null == toInsert)
				{
					// Create the new element.
					toInsert = new Items(_type, _count);
				}
				// However we got here, insert this at the end.
				inventory.items.add(toInsert);
				inventory.currentEncumbrance += encumbranceToAdd;
				didApply = true;
			}
			else
			{
				// This won't fit so fail.
				didApply = false;
			}
		}
		return didApply;
	}
}
