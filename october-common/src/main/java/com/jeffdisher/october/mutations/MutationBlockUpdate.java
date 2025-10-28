package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.GroundCoverHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableInventory;
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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		// Check to see if this block needs to change into a different type due to water, etc.
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			// This is an "empty" type so see if the "empty" blocks around it should influence its type.
			Block newType = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, thisBlock);
			if (newType != thisBlock)
			{
				// This block needs to be changed due to some kind of flowing liquid so schedule the mutation to do that.
				long millisDelay = env.liquids.minFlowDelayMillis(env, newType, thisBlock);
				context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
				didApply = true;
			}
		}
		
		if (!didApply)
		{
			// Make sure that this block can be supported by the one under it.
			AbsoluteLocation belowBlockLocation = _blockLocation.getRelative(0, 0, -1);
			BlockProxy belowBlock = context.previousBlockLookUp.apply(belowBlockLocation);
			Block emptyBlock = env.special.AIR;
			Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
			
			// Note that multi-blocks also can require "existing on block", but only if they are the root block.
			boolean blockIsSupported = env.blocks.canExistOnBlock(thisBlock, (null != belowBlock) ? belowBlock.getBlock() : null);
			if (MultiBlockUtils.isMultiBlockExtension(env, newBlock))
			{
				blockIsSupported = true;
			}
			
			if (!blockIsSupported)
			{
				// The block isn't supported so break it (replace with air) and then see if a liquid needs to change anything).
				if (emptyBlock != eventualBlock)
				{
					long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, thisBlock);
					context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
				}
				
				// Determine if this is a block which breaks normally or if we need to use a special multi-block breaking idiom.
				if (MultiBlockUtils.isMultiBlockRoot(env, newBlock))
				{
					// We will enqueue the MultiBlockReplace for each block in the multi-block, forcing them into air.
					Block existingBlock = newBlock.getBlock();
					MultiBlockUtils.replaceMultiBlock(env, context, _blockLocation, existingBlock, emptyBlock);
				}
				else
				{
					// Create a temporary inventory to drain everything.
					MutableInventory tempInventory = new MutableInventory(Inventory.start(Integer.MAX_VALUE).finish());
					CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(tempInventory, newBlock);
					CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, tempInventory, thisBlock);
					
					// Set the actual block type.
					CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, emptyBlock);
					
					// Drop this fake inventory as passives.
					CommonBlockMutationHelpers.dropTempInventoryAsPassives(context, _blockLocation, tempInventory);
				}
				didApply = true;
			}
			else if (env.blocks.isBrokenByFlowingLiquid(thisBlock) && (emptyBlock != eventualBlock))
			{
				// The block is supported but can be broken by a flowing liquid and flowing liquid should touch it so schedule this update.
				long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, thisBlock);
				context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
				didApply = true;
			}
		}
		
		// Check to see if this has an inventory which should fall (an empty block inventory).
		if (env.blocks.hasEmptyBlockInventory(thisBlock, FlagsAspect.isSet(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE)) && (newBlock.getInventory().currentEncumbrance > 0))
		{
			// We want to say that this did apply if anything happened, including dropping the inventory.
			didApply = CommonBlockMutationHelpers.dropInventoryDownIfNeeded(context, _blockLocation, newBlock)
					|| didApply;
		}
		
		// Handle the case where this might be a hopper.
		if (!didApply && HopperHelpers.isHopper(_blockLocation, newBlock))
		{
			newBlock.requestFutureMutation(MutationBlockPeriodic.MILLIS_BETWEEN_HOPPER_CALLS);
		}
		
		// Check if this was burning and should be extinguished (happens when water flows on top).
		if (FireHelpers.shouldExtinguish(env, context, _blockLocation, newBlock))
		{
			byte flags = newBlock.getFlags();
			flags = FlagsAspect.clear(flags, FlagsAspect.FLAG_BURNING);
			newBlock.setFlags(flags);
			didApply = true;
		}
		
		// Check if we need to destroy any inventory due to fire.
		if (FireHelpers.shouldBurnUpItems(env, context, _blockLocation, newBlock))
		{
			boolean isActive = FlagsAspect.isSet(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
			newBlock.setInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, thisBlock, isActive));
			didApply = true;
		}
		
		// Check if this change was changing the block on top of a ground cover block.
		if (env.groundCover.isGroundCover(newBlock.getBlock()))
		{
			Block reverted = GroundCoverHelpers.checkRevertGroundCover(env, context.previousBlockLookUp, _blockLocation, newBlock.getBlock());
			if (null != reverted)
			{
				CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, reverted);
				didApply = true;
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
				didApply = true;
			}
		}
		
		// See if this block's logical active state should change in response to this update event.
		if (!didApply)
		{
			LogicAspect.ISignalChangeCallback handler = env.logic.blockUpdateHandler(thisBlock);
			if (null != handler)
			{
				OrientationAspect.Direction outputDirection = newBlock.getOrientation();
				boolean isActive = handler.shouldStoreHighSignal(env, context.previousBlockLookUp, _blockLocation, outputDirection);
				byte flags = newBlock.getFlags();
				if (isActive != FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE))
				{
					flags = isActive
							? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
							: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
					;
					newBlock.setFlags(flags);
				}
			}
		}
		return didApply;
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
