package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The common mutation for breaking a block.  If the block is the expected type, it will be replaced by air and dropped
 * as an item in the inventory of the air block.
 */
public class BreakBlockMutation implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final Item _expectedBlockType;

	public BreakBlockMutation(AbsoluteLocation location, Item expectedBlockType)
	{
		_location = location;
		_expectedBlockType = expectedBlockType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		boolean didApply = false;
		// Check to see if this is the expected type.
		if (_expectedBlockType.number() == newBlock.getData15(AspectRegistry.BLOCK))
		{
			// This is a match so replace it with air and place the block in the inventory.
			newBlock.setData15(AspectRegistry.BLOCK, BlockAspect.AIR);
			Inventory oldInventory = newBlock.getDataSpecial(AspectRegistry.INVENTORY);
			// This MUST be null or we were given a bogus expectation type (a container, not a block).
			Assert.assertTrue(null == oldInventory);
			Inventory newInventory = Inventory.start(InventoryAspect.CAPACITY_AIR).add(_expectedBlockType, 1).finish();
			newBlock.setDataSpecial(AspectRegistry.INVENTORY, newInventory);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		// TODO:  Implement.
		throw new AssertionError("Unimplemented - stop-gap");
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// TODO:  Implement.
		throw new AssertionError("Unimplemented - stop-gap");
	}
}
