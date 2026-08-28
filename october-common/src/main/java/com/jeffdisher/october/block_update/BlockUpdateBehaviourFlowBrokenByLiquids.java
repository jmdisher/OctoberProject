package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourFlowBrokenByLiquids implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		CommonBlockMutationHelpers.didScheduleFlowInToBreak(env, context, location, proxy.getBlock());
	}
}
