package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These mutations are created by other block mutations which result in the creation of a space which requires a liquid
 * update.
 * The purpose is to update the state of liquid in the target block, based on the surrounding blocks.  This isn't done
 * inline since liquids should have slower flow rates than the tick rate (as it should vary between them).
 */
public class MutationBlockLiquidFlowInto implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.LIQUID_FLOW_INTO;

	public static MutationBlockLiquidFlowInto deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockLiquidFlowInto(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockLiquidFlowInto(AbsoluteLocation blockLocation)
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
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			Block newType = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, thisBlock);
			if (newType != thisBlock)
			{
				Inventory inv = CommonBlockMutationHelpers.replaceBlockAndRestoreInventory(env, newBlock, newType);
				// The inventory should be restored if these are all empty block types.
				Assert.assertTrue(null == inv);
				thisBlock = newType;
				didApply = true;
			}
		}
		else if (env.blocks.isBrokenByFlowingLiquid(thisBlock))
		{
			// This block can be destroyed by flowing liquids so see if something should flow here.
			Block emptyBlock = env.special.AIR;
			Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
			if (emptyBlock != eventualBlock)
			{
				// We need to drop the block, first.
				Inventory inv = BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, eventualBlock);
				if (null != inv)
				{
					MutableInventory newInventory = new MutableInventory(inv);
					CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(newInventory, newBlock);
					int random0to99 = context.randomInt.applyAsInt(BlockAspect.RANDOM_DROP_LIMIT);
					for (Item dropped : env.blocks.droppedBlocksOnBreak(thisBlock, random0to99))
					{
						newInventory.addItemsAllowingOverflow(dropped, 1);
					}
					// Break the block and replace it with the flowing type, storing the inventory into it (may be over-filled).
					newBlock.setBlockAndClear(eventualBlock);
					newBlock.setInventory(newInventory.freeze());
				}
				else
				{
					newBlock.setBlockAndClear(eventualBlock);
				}
				
				didApply = true;
			}
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

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
