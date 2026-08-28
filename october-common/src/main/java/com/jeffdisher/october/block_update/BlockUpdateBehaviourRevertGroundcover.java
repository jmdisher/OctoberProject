package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourRevertGroundcover implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block reverted = GroundCoverHelpers.checkRevertGroundCover(env, context.previousBlockLookUp, location, proxy.getBlock());
		if (null != reverted)
		{
			CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, proxy, reverted);
		}
	}
}
