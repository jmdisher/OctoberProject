package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These mutations are synthesized by the WorldProcessor, directly, for all the blocks adjacent to a block which changed
 * in the previous tick.
 */
public class MutationBlockUpdate implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.UPDATE;

	public static MutationBlockUpdate deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockUpdate(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockUpdate(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
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
		// Check to see if this block needs to change into a different type due to water, etc.
		Block thisBlock = newBlock.getBlock();
		if (thisBlock.canBeReplaced())
		{
			// This is an "empty" type so see if the "empty" blocks around it should influence its type.
			// Note that we don't change the "source" blocks.
			Block newType = (BlockAspect.getBlock(ItemRegistry.WATER_SOURCE) == thisBlock)
					? thisBlock
					: CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation)
			;
			if (newType != thisBlock)
			{
				// We need to change this so write-back (copy over anything changing).
				Inventory inv = newBlock.getInventory();
				// These block types all support inventories.
				Assert.assertTrue(null != inv);
				newBlock.setBlockAndClear(newType);
				newBlock.setInventory(inv);
				thisBlock = newType;
				didApply = true;
			}
		}
		
		// Check to see if this has an inventory which should fall.
		if (thisBlock.permitsEntityMovement() && (newBlock.getInventory().currentEncumbrance > 0))
		{
			// We want to say that this did apply if anything happened, including dropping the inventory.
			didApply = CommonBlockMutationHelpers.dropInventoryIfNeeded(context, _blockLocation, newBlock)
					|| didApply;
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
	}
}
