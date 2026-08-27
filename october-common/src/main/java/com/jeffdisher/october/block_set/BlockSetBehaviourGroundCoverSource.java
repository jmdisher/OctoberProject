package com.jeffdisher.october.block_set;

import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.mutations.MutationBlockGrowGroundCover;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockSetBehaviourGroundCoverSource implements IBlockSetBehaviour
{
	@Override
	public void didSetBlock(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy, Block replacedType)
	{
		Block newType = proxy.getBlock();
		List<AbsoluteLocation> targets = GroundCoverHelpers.findSpreadNeighbours(env, context.previousBlockLookUp, location, newType);
		for (AbsoluteLocation neighbour : targets)
		{
			MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(neighbour, newType);
			context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
		}
	}
}
