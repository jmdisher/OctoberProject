package com.jeffdisher.october.block_hybrid;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
import com.jeffdisher.october.block_update.IBlockUpdateBehaviour;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.MutationBlockApplyGravity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Handles gravity block behaviour in a set-block or block update case.
 */
public class BlockHybridBehaviourGravity implements IBlockUpdateBehaviour, IBlockSetBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		_apply(env, context, location, proxy);
	}

	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		_apply(env, context, location, proxy);
	}


	private static void _apply(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// If it looks like this should fall, schedule the mutation to apply that.
		BlockProxy belowBlock = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
		if (null != belowBlock)
		{
			if (!env.blocks.isSupportedAgainstGravity(proxy.getBlock(), belowBlock.getBlock()))
			{
				context.mutationSink.next(new MutationBlockApplyGravity(location));
			}
		}
	}
}
