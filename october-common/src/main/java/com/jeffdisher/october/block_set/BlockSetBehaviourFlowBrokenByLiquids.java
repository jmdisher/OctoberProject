package com.jeffdisher.october.block_set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourFlowBrokenByLiquids implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		CommonBlockMutationHelpers.didScheduleFlowInToBreak(env, context, location, proxy.getBlock());
	}
}
