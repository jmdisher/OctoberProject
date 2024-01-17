package com.jeffdisher.october.mutations;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Over-writes a the given block if it is AIR, also destroying any inventory it might have had.
 * Upon failure, no retry is attempted so the block being placed is lost.
 */
public class MutationBlockOverwrite implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final Item _blockType;

	public MutationBlockOverwrite(AbsoluteLocation location, Item blockType)
	{
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(ItemRegistry.AIR != blockType);
		
		_location = location;
		_blockType = blockType;
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
		if (ItemRegistry.AIR.number() == newBlock.getData15(AspectRegistry.BLOCK))
		{
			// Replace the block with the type we have.
			newBlock.setData15(AspectRegistry.BLOCK, _blockType.number());
			// Clear the inventory aspect.
			newBlock.setDataSpecial(AspectRegistry.INVENTORY, null);
			didApply = true;
		}
		return didApply;
	}
}
