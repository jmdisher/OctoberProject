package com.jeffdisher.october.block_hybrid;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.block_set.IBlockSetBehaviour;
import com.jeffdisher.october.block_update.IBlockUpdateBehaviour;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.mutations.MutationBlockGrowGroundCover;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called on a groundcover type in a set-block or block update case.
 */
public class BlockHybridBehaviourBecomeGroundcover implements IBlockUpdateBehaviour, IBlockSetBehaviour
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
		Block shouldBecome = GroundCoverHelpers.findPotentialGroundCoverType(env, context.previousBlockLookUp, location, proxy.getBlock());
		if (null != shouldBecome)
		{
			MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(location, shouldBecome);
			context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
		}
	}
}
