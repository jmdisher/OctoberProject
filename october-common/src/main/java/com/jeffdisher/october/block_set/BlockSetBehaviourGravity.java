package com.jeffdisher.october.block_set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.MutationBlockApplyGravity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourGravity implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		// If we think that this should fall, schedule the apply gravity mutation.
		BlockProxy belowBlock = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
		if (null != belowBlock)
		{
			Block newType = proxy.getBlock();
			if (!env.blocks.isSupportedAgainstGravity(newType, belowBlock.getBlock()))
			{
				context.mutationSink.next(new MutationBlockApplyGravity(location));
			}
		}
	}
}
