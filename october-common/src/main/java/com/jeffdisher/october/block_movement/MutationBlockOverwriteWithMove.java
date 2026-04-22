package com.jeffdisher.october.block_movement;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A mutation which will overwrite any existing block with a given MovableBlockData object.  If shouldDropExisting is
 * set, it will also drop the overwritten block as a passive on top of this.
 * NOTE:  This is expected to only be used in transactions (related to block movement) so it isn't serialized.
 */
public class MutationBlockOverwriteWithMove implements IMutationBlock
{
	private final AbsoluteLocation _blockLocation;
	private final MovableBlockData _blockData;
	private final boolean _shouldDropExisting;

	public MutationBlockOverwriteWithMove(AbsoluteLocation blockLocation, MovableBlockData blockData, boolean shouldDropExisting)
	{
		_blockLocation = blockLocation;
		_blockData = blockData;
		_shouldDropExisting = shouldDropExisting;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		if (_shouldDropExisting)
		{
			Environment env = Environment.getShared();
			Block thisBlock = newBlock.getBlock();
			
			// We need to check if this is the kind of block which breaks.
			if (!env.blocks.canBeReplaced(thisBlock))
			{
				MutableInventory tempInventory = new MutableInventory(Inventory.start(Integer.MAX_VALUE).finish());
				CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, tempInventory, thisBlock);
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(tempInventory, newBlock);
				CommonBlockMutationHelpers.dropTempInventoryAsPassives(context, _blockLocation, tempInventory);
			}
		}
		
		// Blindly overwrite this data.
		_blockData.clearProxyAndApply(newBlock);
	}

	@Override
	public MutationBlockType getType()
	{
		// Not serialized so we don't need a type.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This is only used in transactions, which aren't saved to disk.
		return false;
	}
}
