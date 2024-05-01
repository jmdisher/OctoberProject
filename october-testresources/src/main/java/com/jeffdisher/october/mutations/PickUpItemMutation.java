package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.StationRegistry;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This mutation is specifically made to pick up items from the ground.  This means it only applies to air blocks.
 * The mutation is purely atomic:  It will either pick up the requested amount or none and fail.
 * Additionally, this mutation cannot be used for groups of items which are larger than what a single air block can
 * hold.
 */
public class PickUpItemMutation implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final Item _type;
	private final int _count;

	public PickUpItemMutation(AbsoluteLocation location, Item type, int count)
	{
		Environment env = Environment.getShared();
		// We don't allow creation of this mutation if the items couldn't possibly be on the ground.
		Assert.assertTrue((env.encumbrance.getEncumbrance(type) * count) <= StationRegistry.CAPACITY_BLOCK_EMPTY);
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
		Environment env = Environment.getShared();
		// We can only operator on air.
		boolean didApply = false;
		if (env.special.AIR == newBlock.getBlock())
		{
			Inventory oldInventory = newBlock.getInventory();
			MutableInventory mutable = new MutableInventory(oldInventory);
			if (mutable.getCount(_type) >= _count)
			{
				mutable.removeStackableItems(_type, _count);
				newBlock.setInventory(mutable.freeze());
				didApply = true;
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

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
