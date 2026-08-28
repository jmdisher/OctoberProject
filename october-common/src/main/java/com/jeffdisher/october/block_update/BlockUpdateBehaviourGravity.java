package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.MutationBlockApplyGravity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourGravity implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
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
