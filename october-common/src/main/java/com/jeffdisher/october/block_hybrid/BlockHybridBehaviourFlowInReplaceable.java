package com.jeffdisher.october.block_hybrid;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
import com.jeffdisher.october.block_update.IBlockUpdateBehaviour;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called in both a set-block or block update case.
 */
public class BlockHybridBehaviourFlowInReplaceable implements IBlockUpdateBehaviour, IBlockSetBehaviour
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
		MutationBlockLiquidFlowInto.didScheduleFlowInForReplaceable(env, context, location, proxy);
	}
}
