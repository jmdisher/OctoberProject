package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
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
		Environment env = Environment.getShared();
		boolean didApply = false;
		// Check to see if this block needs to change into a different type due to water, etc.
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			// This is an "empty" type so see if the "empty" blocks around it should influence its type.
			// Note that we don't change the "source" blocks.
			Block newType = (env.special.WATER_SOURCE == thisBlock)
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
		
		if (!didApply)
		{
			// Make sure that this block can be supported by the one under it.
			AbsoluteLocation belowBlockLocation = _blockLocation.getRelative(0, 0, -1);
			BlockProxy belowBlock = context.previousBlockLookUp.apply(belowBlockLocation);
			boolean blockIsSupported = env.blocks.canExistOnBlock(thisBlock, (null != belowBlock) ? belowBlock.getBlock() : null);
			if (!blockIsSupported)
			{
				// We have decided to break this block so determine what block it will become.
				Block emptyBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation);
				
				// Create the inventory for this type.
				MutableInventory newInventory = new MutableInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, emptyBlock));
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(newInventory, newBlock);
				
				// Add this block's drops to the inventory.
				for (Item dropped : env.blocks.droppedBlocksOnBreak(thisBlock))
				{
					newInventory.addItemsAllowingOverflow(dropped, 1);
				}
				
				// Break the block and replace it with the empty type, storing the inventory into it (may be over-filled).
				newBlock.setBlockAndClear(emptyBlock);
				newBlock.setInventory(newInventory.freeze());
				didApply = true;
			}
		}
		
		// Check to see if this has an inventory which should fall.
		if (env.blocks.permitsEntityMovement(thisBlock) && (newBlock.getInventory().currentEncumbrance > 0))
		{
			// We want to say that this did apply if anything happened, including dropping the inventory.
			didApply = CommonBlockMutationHelpers.dropInventoryIfNeeded(context, _blockLocation, newBlock)
					|| didApply;
		}
		
		// Handle the case where this might be a hopper.
		HopperHelpers.tryProcessHopper(context, _blockLocation, newBlock);
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

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
