package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LiquidRegistry;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.types.TickProcessingContext;


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
	 * Looks at the blocks around the given location to determine what the correct "empty" block type should be put in
	 * this location.
	 * Note that this doesn't account for the current block type in the location so this shouldn't be used if that value
	 * should not be over-ridden.
	 * 
	 * @param context The context.
	 * @param location The location to investigate.
	 * @param currentBlock The current block contents (not read from context since it could be changing in caller).
	 * @return The block type which the surrounding blocks imply the location should become.
	 */
	public static Pair<Block, LiquidRegistry.LiquidBlock> determineEmptyBlockType(TickProcessingContext context, AbsoluteLocation location, LiquidRegistry.LiquidBlock currentBlock)
	{
		return _determineEmptyBlockType(context, location, currentBlock);
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
}
