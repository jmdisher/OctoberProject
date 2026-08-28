package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are synthesized by the WorldProcessor, directly, for all the blocks adjacent to a block which changed
 * in the previous tick.
 */
public class MutationBlockUpdate implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.UPDATE;

	public static MutationBlockUpdate deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockUpdate(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockUpdate(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		env.hooks.doRunUpdate(env, context, _blockLocation, newBlock);
		
		// Make sure that this block can be supported - see if we can read the orientation.
		AbsoluteLocation supportLocation;
		if (env.orientations.doesSingleBlockRequireOrientation(newBlock.getBlock()))
		{
			FacingDirection output = newBlock.getOrientation();
			supportLocation = output.getOutputBlockLocation(_blockLocation);
		}
		else
		{
			supportLocation = _blockLocation.getRelative(0, 0, -1);
		}
		
		BlockProxy supportBlock = context.previousBlockLookUp.readBlock(supportLocation);
		
		// Note that multi-blocks also can require "existing on block", but only if they are the root block.
		boolean blockIsSupported = env.blocks.canExistOnBlock(newBlock.getBlock(), (null != supportBlock) ? supportBlock.getBlock() : null);
		if (MultiBlockUtils.isMultiBlockExtension(env, newBlock))
		{
			blockIsSupported = true;
		}
		
		if (!blockIsSupported)
		{
			// Determine if this is a block which breaks normally or if we need to use a special multi-block breaking idiom.
			if (MultiBlockUtils.isMultiBlockRoot(env, newBlock))
			{
				// We will enqueue the MultiBlockReplace for each block in the multi-block, forcing them into air.
				Block existingBlock = newBlock.getBlock();
				Block emptyBlock = env.special.AIR;
				MultiBlockUtils.replaceMultiBlock(env, context, _blockLocation, existingBlock, emptyBlock);
			}
			else
			{
				// Create a temporary inventory to drain everything.
				CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, _blockLocation, newBlock.getBlock());
				CommonBlockMutationHelpers.dropBlockInventoriesAsPassives(context, _blockLocation, newBlock);
				
				// Destroy the block.
				CommonBlockMutationHelpers.setEmptyBlock(env, context, _blockLocation, newBlock);
				
				CommonBlockMutationHelpers.didScheduleFlowInForReplaceable(env, context, _blockLocation, newBlock);
			}
		}
		
		// Check to see if this block needs to change into a different type due to water, etc.
		if (env.blocks.canBeReplaced(newBlock.getBlock()))
		{
			// This is an "empty" type so see if the "empty" blocks around it should influence its type.
			CommonBlockMutationHelpers.didScheduleFlowInForReplaceable(env, context, _blockLocation, newBlock);
		}
		if (env.blocks.isBrokenByFlowingLiquid(newBlock.getBlock()))
		{
			CommonBlockMutationHelpers.didScheduleFlowInToBreak(env, context, _blockLocation, newBlock.getBlock());
		}
		if (env.blocks.hasGravity(newBlock.getBlock()))
		{
			// If it looks like this should fall, schedule the mutation to apply that.
			BlockProxy belowBlock = context.previousBlockLookUp.readBlock(_blockLocation.getRelative(0, 0, -1));
			if (null != belowBlock)
			{
				if (!env.blocks.isSupportedAgainstGravity(newBlock.getBlock(), belowBlock.getBlock()))
				{
					context.mutationSink.next(new MutationBlockApplyGravity(_blockLocation));
				}
			}
		}
		
		// Check if this was burning and should be extinguished (happens when water flows on top).
		if (FireHelpers.shouldExtinguish(env, context, _blockLocation, newBlock))
		{
			byte flags = newBlock.getFlags();
			flags = FlagsAspect.clear(flags, FlagsAspect.FLAG_BURNING);
			newBlock.setFlags(flags);
		}
		
		// Check if this change was changing the block on top of a ground cover block.
		if (env.groundCover.isGroundCover(newBlock.getBlock()))
		{
			Block reverted = GroundCoverHelpers.checkRevertGroundCover(env, context.previousBlockLookUp, _blockLocation, newBlock.getBlock());
			if (null != reverted)
			{
				CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, _blockLocation, newBlock, reverted);
			}
		}
		
		// Check if this block could become ground cover.
		if (null != env.groundCover.canGrowGroundCover(newBlock.getBlock()))
		{
			Block shouldBecome = GroundCoverHelpers.findPotentialGroundCoverType(env, context.previousBlockLookUp, _blockLocation, newBlock.getBlock());
			if (null != shouldBecome)
			{
				MutationBlockGrowGroundCover grow = new MutationBlockGrowGroundCover(_blockLocation, shouldBecome);
				context.mutationSink.future(grow, MutationBlockGrowGroundCover.SPREAD_DELAY_MILLIS);
				// We did do something, even if it didn't change this block, so return true.
			}
		}
		
		// See if this block's logical active state should change in response to this update event.
		LogicAspect.ISignalChangeCallback handler = env.logic.blockUpdateHandler(newBlock.getBlock());
		if (null != handler)
		{
			FacingDirection outputDirection = newBlock.getOrientation();
			boolean isActive = handler.shouldStoreHighSignal(env, context.previousBlockLookUp, _blockLocation, outputDirection);
			byte flags = newBlock.getFlags();
			if (isActive != FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE))
			{
				flags = isActive
					? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
					: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
				;
				newBlock.setFlags(flags);
				
				// Note that we keep the change of block ACTIVE state and the response to this change as 2 distinct callbacks.
				LogicAspect.IActiveFlagChangeCallback changeState = env.logic.flagChangeHandler(newBlock.getBlock());
				if (null != changeState)
				{
					changeState.activeFlagDidChange(context, newBlock, _blockLocation, isActive);
				}
			}
		}
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
