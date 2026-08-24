package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.CommonBlockMutationHelpers;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers for managing plant growth.
 */
public class PlantHelpers
{
	public static final byte MIN_LIGHT = 5;

	/**
	 * Used to check if the given block type is one which can grow.
	 * 
	 * @param env The environment.
	 * @param block The existing block type.
	 * @return True if this block type can grow.
	 */
	public static boolean canGrow(Environment env, Block block)
	{
		int growthDivisor = env.plants.growthDivisor(block);
		return (growthDivisor > 0);
	}

	/**
	 * Attempts to perform a growth operation on the given newBlock, returning whether or not another growth operation
	 * should be scheduled (meaning it either didn't grow or still has more growing to do).
	 * 
	 * @param env The environment.
	 * @param context The context.
	 * @param location The location where the growth is happening.
	 * @param newBlock The mutable block which we should grow (block type can be modified).
	 * @return True if another growth operation should be scheduled.
	 */
	public static boolean shouldRescheduleAfterPlantPeriodic(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		// See if the random generator says we should grow this tick or try again later.
		// We will only bother if the block is lit.
		boolean isLit = (newBlock.getLight() >= MIN_LIGHT) || (context.skyLight.lookup(location) >= MIN_LIGHT);
		int growthDivisor = env.plants.growthDivisor(newBlock.getBlock());
		// This MUST be something which can grow.
		Assert.assertTrue(growthDivisor > 0);
		int randomBits = context.randomInt.applyAsInt(growthDivisor);
		boolean canGrow = isLit && (1 == randomBits);
		boolean shouldReschedule;
		if (canGrow)
		{
			shouldReschedule = _shouldRescheduleAfterGrowth(env, context, location, newBlock);
		}
		else
		{
			// Just reschedule this.
			shouldReschedule = true;
		}
		return shouldReschedule;
	}

	/**
	 * Performs a growth operation on the given newBlock.
	 * 
	 * @param env The environment.
	 * @param context The context.
	 * @param location The location where the growth is happening.
	 * @param newBlock The mutable block which we should grow (block type can be modified).
	 */
	public static void performForcedGrow(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		int growthDivisor = env.plants.growthDivisor(newBlock.getBlock());
		// This MUST be something which can grow.
		Assert.assertTrue(growthDivisor > 0);
		_shouldRescheduleAfterGrowth(env, context, location, newBlock);
	}


	private static boolean _shouldRescheduleAfterGrowth(Environment env, TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		boolean shouldReschedule;
		Block block = newBlock.getBlock();
		
		// Check the different ways that this might need to grow.
		if (env.plants.isTree(block))
		{
			_growTree(context, location, newBlock);
			shouldReschedule = false;
		}
		else
		{
			// If not a tree, we are using the staged growth system so load our count of completed growth stages.
			byte growthPhasesCompleted = newBlock.getBlockDefinedByte();
			
			// We will add 1 and check if this means we are done.
			growthPhasesCompleted += 1;
			if (growthPhasesCompleted >= env.plants.growthStagesForPlant(block))
			{
				// Replace this with the mature block type.
				Block matureBlock = env.plants.matureBlockForPlant(block);
				CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, newBlock, matureBlock);
				shouldReschedule = (env.plants.growthDivisor(matureBlock) > 0);
			}
			else
			{
				// Not done yet so just resave the growth progress.
				newBlock.setBlockDefinedByte(growthPhasesCompleted);
				shouldReschedule = true;
			}
		}
		return shouldReschedule;
	}

	private static void _growTree(TickProcessingContext context, AbsoluteLocation location, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		Block log = env.special.blockLog;
		Block leaf = env.special.blockLeaf;
		// Replace this with a log and leaf blocks.
		// TODO:  Figure out how to make more interesting trees.
		
		CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, location, newBlock, log);
		_tryScheduleBlockOverwrite(env, context, location,  log,  0,  0,  1);
		_tryScheduleBlockOverwrite(env, context, location, leaf, -1,  0,  1);
		_tryScheduleBlockOverwrite(env, context, location, leaf,  1,  0,  1);
		_tryScheduleBlockOverwrite(env, context, location, leaf,  0, -1,  1);
		_tryScheduleBlockOverwrite(env, context, location, leaf,  0,  1,  1);
	}

	private static void _tryScheduleBlockOverwrite(Environment env, TickProcessingContext context, AbsoluteLocation topLocation, Block blockType, int x, int y, int z)
	{
		AbsoluteLocation location = topLocation.getRelative(x, y, z);
		BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
		if (null != proxy)
		{
			Block block = proxy.getBlock();
			if (env.blocks.canBeReplaced(block))
			{
				context.mutationSink.next(new MutationBlockOverwriteInternal(location, blockType));
			}
		}
	}
}
