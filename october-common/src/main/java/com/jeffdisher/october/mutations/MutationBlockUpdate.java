package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are synthesized by the WorldProcessor, directly, for all the blocks adjacent to a block which changed
 * in the previous tick.
 */
public class MutationBlockUpdate implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.UPDATE;

	public static MutationBlockUpdate deserializeFromBuffer(ByteBuffer buffer)
	{
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
			boolean blockIsSupported = env.blocks.canExistOnBlock(thisBlock, (null != belowBlock) ? belowBlock.getBlock() : null);
			Block emptyBlock = env.special.AIR;
			Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
			
			if (!blockIsSupported)
			{
				// The block isn't supported so break it (replace with air) and then see if a liquid needs to change anything).
				if (emptyBlock != eventualBlock)
				{
					long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, thisBlock);
					context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
				}
				
				// Create the inventory for this type.
				MutableInventory newInventory = new MutableInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, emptyBlock));
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(newInventory, newBlock);
				
				// Add this block's drops to the inventory.
				CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, newInventory, thisBlock);
				
				// Break the block and replace it with the empty type, storing the inventory into it (may be over-filled).
				CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, emptyBlock);
				newBlock.setInventory(newInventory.freeze());
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
		if (env.blocks.hasEmptyBlockInventory(thisBlock) && (newBlock.getInventory().currentEncumbrance > 0))
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
			newBlock.setInventory(BlockProxy.getDefaultNormalOrEmptyBlockInventory(env, thisBlock));
			didApply = true;
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
