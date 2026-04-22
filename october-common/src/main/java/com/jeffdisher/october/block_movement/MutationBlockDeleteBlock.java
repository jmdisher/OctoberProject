package com.jeffdisher.october.block_movement;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A simple mutation which purely destroys a block, replacing it with air and potentially scheduling liquid updates.
 * NOTE:  This is expected to only be used in transactions (related to block movement) so it isn't serialized.
 */
public class MutationBlockDeleteBlock implements IMutationBlock
{
	private final AbsoluteLocation _blockLocation;

	public MutationBlockDeleteBlock(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// First, set the block to air.
		Environment env = Environment.getShared();
		Block emptyBlock = env.special.AIR;
		newBlock.setBlockAndClear(emptyBlock);
		
		// Now, determine if a liquid needs to flow into the space.
		Block eventualType = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
		if (emptyBlock != eventualType)
		{
			long millisDelay = env.liquids.flowDelayMillis(eventualType);
			context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
		}
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
