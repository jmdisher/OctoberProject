package com.jeffdisher.october.mutations;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The DropItemMutation is specifically made to drop items onto the ground.  This means it will only drop into air
 * blocks.
 * The mutation is purely atomic:  It will either drop all of the items or destroy all of them and fail.
 * Additionally, this mutation cannot be used for groups of items which are larger than what a single air block can
 * hold.
 */
public class DropItemMutation implements IMutationBlock
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
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		return _common(newBlock);
	}


	private boolean _common(MutableBlockProxy newBlock)
	{
		// We can only drop an item into air.
		boolean didApply = false;
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		if (BlockAspect.AIR == oldValue)
		{
			// Disect existing inventory into mutable copies or create defaults.
			int maxEncumbrance = InventoryAspect.CAPACITY_AIR;
			int currentEncumbrance = 0;
			Inventory oldInventory = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
			if (null != oldInventory)
			{
				// We checked that this was air so it should match.
				Assert.assertTrue(maxEncumbrance == oldInventory.maxEncumbrance);
				currentEncumbrance = oldInventory.currentEncumbrance;
			}
			// Check if this will fit (note that a fresh inventory will _always_ fit (see assertion in constructor).
			int encumbranceToAdd = _type.encumbrance() * _count;
			if (encumbranceToAdd <= (maxEncumbrance - currentEncumbrance))
			{
				// This will fit so see if there is an existing Items to which we can add this or if we need to just insert this.
				Items existing = (null != oldInventory)
						? oldInventory.items.get(_type)
						: null
				;
				Items toInsert = (null != existing)
						? new Items(_type, existing.count() + _count)
						: new Items(_type, _count)
				;
				Map<Item, Items> mutableItemMap = new HashMap<>();
				if (null != oldInventory)
				{
					mutableItemMap.putAll(oldInventory.items);
				}
				mutableItemMap.put(_type, toInsert);
				currentEncumbrance += encumbranceToAdd;
				
				// Now, update the block with the new inventory.
				newBlock.setDataSpecial(AspectRegistry.INVENTORY, new Inventory(maxEncumbrance, mutableItemMap, currentEncumbrance));
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
