package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourFlowInReplaceable implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// This is an "empty" type so see if the "empty" blocks around it should influence its type.
		MutationBlockLiquidFlowInto.didScheduleFlowInForReplaceable(env, context, location, proxy);
	}
}
