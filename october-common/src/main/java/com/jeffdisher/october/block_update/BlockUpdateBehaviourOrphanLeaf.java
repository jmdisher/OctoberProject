package com.jeffdisher.october.block_update;

import java.util.List;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourOrphanLeaf implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// Check all 6 faces of this block for log blocks.
		// If there are none, break this block (allowing its normal drops as passives).
		List<AbsoluteLocation> faces = List.of(location.getRelative(0, 0, -1)
			, location.getRelative(0, 0, 1)
			, location.getRelative(0, -1, 0)
			, location.getRelative(0, 1, 0)
			, location.getRelative(-1, 0, 0)
			, location.getRelative(1, 0, 0)
		);
		Map<AbsoluteLocation, BlockProxy> proxies = context.previousBlockLookUp.readBlockBatch(faces);
		boolean didFindLog = false;
		for (BlockProxy aProxy : proxies.values())
		{
			if (env.special.blockLog == aProxy.getBlock())
			{
				didFindLog = true;
				break;
			}
		}
		
		if (!didFindLog)
		{
			int noEntityId = 0;
			CommonBlockMutationHelpers.breakBlockAndHandleFollowUp(env, context, location, proxy, noEntityId);
		}
	}
}
