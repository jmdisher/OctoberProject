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
 * This mutation is specifically made to pick up items from the ground.  This means it only applies to air blocks.
 * The mutation is purely atomic:  It will either pick up the requested amount or none and fail.
 * Additionally, this mutation cannot be used for groups of items which are larger than what a single air block can
 * hold.
 */
public class PickUpItemMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final Item _type;
	private final int _count;

	public PickUpItemMutation(AbsoluteLocation location, Item type, int count)
	{
		// We don't allow creation of this mutation if the items couldn't possibly be on the ground.
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
			reverseMutation = new DropItemMutation(_location, _type, _count);
		}
		return reverseMutation;
	}


	private boolean _common(MutableBlockProxy newBlock)
	{
		// We can only operator on air.
		boolean didApply = false;
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		if (BlockAspect.AIR == oldValue)
		{
			Inventory inventory = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
			if (null != inventory)
			{
				// Walk the items in the inventory and remove the number of the type of elements.
				Items updated = null;
				Iterator<Items> iter = inventory.items.iterator();
				while (iter.hasNext())
				{
					Items items = iter.next();
					if (items.type() == _type)
					{
						if (items.count() >= _count)
						{
							iter.remove();
							int remaining = items.count() - _count;
							if (remaining > 0)
							{
								updated = new Items(_type, remaining);
							}
							didApply = true;
							break;
						}
					}
				}
				if (null != updated)
				{
					inventory.items.add(updated);
				}
				else if (inventory.items.isEmpty())
				{
					// If the list is now empty, remove the special.
					newBlock.setDataSpecial(AspectRegistry.INVENTORY, null);
				}
			}
		}
		return didApply;
	}
}
