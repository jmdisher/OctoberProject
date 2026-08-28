package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LiquidRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IBlockProxy;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These mutations are created by other block mutations which result in the creation of a space which requires a liquid
 * update.
 * The purpose is to update the state of liquid in the target block, based on the surrounding blocks.  This isn't done
 * inline since liquids should have slower flow rates than the tick rate (as it should vary between them).
 */
public class MutationBlockLiquidFlowInto implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.LIQUID_FLOW_INTO;

	public static MutationBlockLiquidFlowInto deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockLiquidFlowInto(location);
	}

	/**
	 * Checks to see if a replaceable block is adjacent to liquids which should flow into it and interact.  Returns true
	 * if the follow-up mutation to accomplish this was scheduled in the given context.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of the check.
	 * @param proxy The proxy for the block to check.
	 * @return True if a MutationBlockLiquidFlowInto was scheduled for this block.
	 */
	public static boolean didScheduleFlowInForReplaceable(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, IBlockProxy proxy
	)
	{
		// We expect that this is only called when the block can be replaced.
		Block blockType = proxy.getBlock();
		Assert.assertTrue(env.blocks.canBeReplaced(blockType));
		
		// This case is used when not changing the type so we use the same for new and old (only used to choose a delay).
		// We need to make sure that the eventual type is a mismatch but also that it has a flow rate (otherwise, placing a water source surrounded by air will think it should be air, meaning it should reflow immediately).
		LiquidRegistry.LiquidBlock currentLiquid = env.liquids.pairFrom(proxy).two();
		return _didScheduleFlowInto(env, context, location, currentLiquid);
	}

	/**
	 * Checks to see if a block which can be broken by liquids is adjacent to liquids which should flow into it and
	 * interact.  Returns true if the follow-up mutation to accomplish this was scheduled in the given context.
	 * 
	 * @param env The environment.
	 * @param context The context for looking up blocks and scheduling mutations.
	 * @param location The location of the check.
	 * @param blockType The current type of block (must be replaceable).
	 * @return True if a MutationBlockLiquidFlowInto was scheduled for this block.
	 */
	public static boolean didScheduleFlowInToBreak(Environment env
		, TickProcessingContext context
		, AbsoluteLocation location
		, Block blockType
	)
	{
		// We expect that this is only called when the block can be broken by liquids.
		Assert.assertTrue(env.blocks.isBrokenByFlowingLiquid(blockType));
		
		LiquidRegistry.LiquidBlock emptyBlock = null;
		return _didScheduleFlowInto(env, context, location, emptyBlock);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockLiquidFlowInto(AbsoluteLocation blockLocation)
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
		
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			LiquidRegistry.LiquidBlock liquidBlock = env.liquids.pairFrom(newBlock).two();
			Pair<Block, LiquidRegistry.LiquidBlock> newType = _determineEmptyBlockType(context, _blockLocation, liquidBlock);
			
			if (null != newType.one())
			{
				_updateBlock(env, context, newBlock, newType.one());
			}
			else
			{
				CommonBlockMutationHelpers.setLiquidWithFollowUps(env, context, _blockLocation, newBlock, newType.two());
			}
		}
		else if (env.blocks.isBrokenByFlowingLiquid(thisBlock))
		{
			// This block can be destroyed by flowing liquids so see if something should flow here.
			LiquidRegistry.LiquidBlock emptyBlock = null;
			Pair<Block, LiquidRegistry.LiquidBlock> eventualBlock = _determineEmptyBlockType(context, _blockLocation, emptyBlock);
			
			if (null != eventualBlock.one())
			{
				if (eventualBlock.one() != env.special.AIR)
				{
					CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, _blockLocation, thisBlock);
					CommonBlockMutationHelpers.dropBlockInventoriesAsPassives(context, _blockLocation, newBlock);
					
					_updateBlock(env, context, newBlock, eventualBlock.one());
				}
			}
			else
			{
				CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, _blockLocation, thisBlock);
				CommonBlockMutationHelpers.dropBlockInventoriesAsPassives(context, _blockLocation, newBlock);
				
				CommonBlockMutationHelpers.setLiquidWithFollowUps(env, context, _blockLocation, newBlock, eventualBlock.two());
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


	private void _updateBlock(Environment env, TickProcessingContext context, IMutableBlockProxy proxy, Block newBlock)
	{
		if (env.special.AIR == newBlock)
		{
			CommonBlockMutationHelpers.setEmptyBlock(env, context, _blockLocation, proxy);
		}
		else
		{
			CommonBlockMutationHelpers.setBlockWithFollowUps(env, context, _blockLocation, proxy, newBlock);
		}
	}

	private static Pair<Block, LiquidRegistry.LiquidBlock> _determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentBlock)
	{
		Environment env = Environment.getShared();
		
		LiquidRegistry.LiquidBlock east = _getLiquidOrNull(env, context, location.getRelative(1, 0, 0));
		LiquidRegistry.LiquidBlock west = _getLiquidOrNull(env, context, location.getRelative(-1, 0, 0));
		LiquidRegistry.LiquidBlock north = _getLiquidOrNull(env, context, location.getRelative(0, 1, 0));
		LiquidRegistry.LiquidBlock south = _getLiquidOrNull(env, context, location.getRelative(0, -1, 0));
		LiquidRegistry.LiquidBlock up = _getLiquidOrNull(env, context, location.getRelative(0, 0, 1));
		
		BlockProxy downProxy = context.previousBlockLookUp.readBlock(location.getRelative(0, 0, -1));
		Block down = (null != downProxy)
			? downProxy.getBlock()
			: null
		;
		if ((null != down) && env.blocks.canBeReplaced(down))
		{
			down = null;
		}
		return env.liquids.chooseEmptyLiquidBlock(env, currentBlock, east, west, north, south, up, down);
	}

	private static LiquidRegistry.LiquidBlock _getLiquidOrNull(Environment env, TickProcessingContext context, AbsoluteLocation location)
	{
		BlockProxy proxy = context.previousBlockLookUp.readBlock(location);
		return (null != proxy)
			? env.liquids.pairFrom(proxy).two()
			: null
		;
	}

	private static boolean _didScheduleFlowInto(Environment env, TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentLiquid)
	{
		Pair<Block, LiquidRegistry.LiquidBlock> eventualType = _determineEmptyBlockType(context, location, currentLiquid);
		LiquidRegistry.LiquidBlock eventualLiquid = eventualType.two();
		Block eventualBlock = eventualType.one();
		if (env.special.AIR == eventualBlock)
		{
			eventualBlock = null;
		}
		
		boolean didScheduleLiquid = false;
		if ((null != eventualBlock) || !_doLiquidsMatch(currentLiquid, eventualLiquid))
		{
			Block currentLiquidSource = (null != currentLiquid)
				? currentLiquid.sourceType()
				: null
			;
			Block eventualLiquidSource = (null != eventualLiquid)
				? eventualLiquid.sourceType()
				: null
			;
			
			// It is possible that neither of these exist (if this is a solid forming from 2 flowing neighbours), so pick a good default.
			long millisDelay = 1000L;
			if (null != currentLiquidSource)
			{
				long currentMillis = env.liquids.flowDelayMillis(currentLiquidSource);
				millisDelay = Math.min(millisDelay, currentMillis);
			}
			if (null != eventualLiquidSource)
			{
				long eventualMillis = env.liquids.flowDelayMillis(eventualLiquidSource);
				millisDelay = Math.min(millisDelay, eventualMillis);
			}
			Assert.assertTrue(millisDelay > 0L);
			
			context.mutationSink.future(new MutationBlockLiquidFlowInto(location), millisDelay);
			didScheduleLiquid = true;
		}
		return didScheduleLiquid;
	}

	private static boolean _doLiquidsMatch(LiquidRegistry.LiquidBlock one, LiquidRegistry.LiquidBlock two)
	{
		Block oneBlock = (null != one)
			? one.sourceType()
			: null
		;
		byte oneDistance = (null != one)
			? one.distance()
			: LiquidRegistry.FLOW_NONE
		;
		Block twoBlock = (null != two)
			? two.sourceType()
			: null
		;
		byte twoDistance = (null != two)
			? two.distance()
			: LiquidRegistry.FLOW_NONE
		;
		
		return ((oneBlock == twoBlock) && (oneDistance == twoDistance));
	}
}
