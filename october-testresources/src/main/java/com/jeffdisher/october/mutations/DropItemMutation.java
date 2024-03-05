package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		return _common(newBlock);
	}


	private boolean _common(IMutableBlockProxy newBlock)
	{
		// We can only drop an item into air.
		boolean didApply = false;
		if (ItemRegistry.AIR == newBlock.getItem())
		{
			// Disect existing inventory into mutable copies or create defaults.
			Inventory oldInventory = newBlock.getInventory();
			MutableInventory mutable = new MutableInventory(oldInventory);
			if (mutable.addAllItems(_type, _count))
			{
				// Now, update the block with the new inventory.
				newBlock.setInventory(mutable.freeze());
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

	@Override
	public MutationBlockType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
