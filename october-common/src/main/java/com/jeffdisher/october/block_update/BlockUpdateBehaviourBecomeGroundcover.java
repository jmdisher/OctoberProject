package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.mutations.MutationBlockGrowGroundCover;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourBecomeGroundcover implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		Block shouldBecome = GroundCoverHelpers.findPotentialGroundCoverType(env, context.previousBlockLookUp, location, proxy.getBlock());
		if (null != shouldBecome)
		{
			MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(location, shouldBecome);
			context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
		}
	}
}
