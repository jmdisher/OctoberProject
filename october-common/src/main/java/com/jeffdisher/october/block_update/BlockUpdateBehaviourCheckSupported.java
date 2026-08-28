package com.jeffdisher.october.block_update;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.mutations.MultiBlockUtils;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;


public class BlockUpdateBehaviourCheckSupported implements IBlockUpdateBehaviour
{
	@Override
	public void doRunUpdate(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy proxy)
	{
		// Make sure that this block can be supported - see if we can read the orientation.
		AbsoluteLocation supportLocation;
		if (env.orientations.doesSingleBlockRequireOrientation(proxy.getBlock()))
		{
			FacingDirection output = proxy.getOrientation();
			supportLocation = output.getOutputBlockLocation(location);
		}
		else
		{
			supportLocation = location.getRelative(0, 0, -1);
		}
		
		BlockProxy supportBlock = context.previousBlockLookUp.readBlock(supportLocation);
		
		// Note that multi-blocks also can require "existing on block", but only if they are the root block.
		boolean blockIsSupported = env.blocks.canExistOnBlock(proxy.getBlock(), (null != supportBlock) ? supportBlock.getBlock() : null);
		if (MultiBlockUtils.isMultiBlockExtension(env, proxy))
		{
			blockIsSupported = true;
		}
		
		if (!blockIsSupported)
		{
			// Determine if this is a block which breaks normally or if we need to use a special multi-block breaking idiom.
			if (MultiBlockUtils.isMultiBlockRoot(env, proxy))
			{
				// We will enqueue the MultiBlockReplace for each block in the multi-block, forcing them into air.
				Block existingBlock = proxy.getBlock();
				Block emptyBlock = env.special.AIR;
				MultiBlockUtils.replaceMultiBlock(env, context, location, existingBlock, emptyBlock);
			}
			else
			{
				// Create a temporary inventory to drain everything.
				CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, location, proxy.getBlock());
				CommonBlockMutationHelpers.dropBlockInventoriesAsPassives(context, location, proxy);
				
				// Destroy the block.
				CommonBlockMutationHelpers.setEmptyBlock(env, context, location, proxy);
				
				MutationBlockLiquidFlowInto.didScheduleFlowInForReplaceable(env, context, location, proxy);
			}
		}
	}
}
