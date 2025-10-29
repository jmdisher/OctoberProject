package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableInventory;
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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		
		boolean didApply = false;
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			Block newType = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, thisBlock);
			if (newType != thisBlock)
			{
				CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, newType);
				thisBlock = newType;
				didApply = true;
			}
		}
		else if (env.blocks.isBrokenByFlowingLiquid(thisBlock))
		{
			// This block can be destroyed by flowing liquids so see if something should flow here.
			Block emptyBlock = env.special.AIR;
			Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
			if (emptyBlock != eventualBlock)
			{
				// We will populate a MutableInventory (since it can collect like types) and then walk this union of all
				// drops to generate passives.
				// NOTE:  This approach assumes that a flowing block CANNOT also have an inventory.
				MutableInventory tempInventory = new MutableInventory(Inventory.start(Integer.MAX_VALUE).finish());
				CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, tempInventory, thisBlock);
				CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(tempInventory, newBlock);
				CommonBlockMutationHelpers.dropTempInventoryAsPassives(context, _blockLocation, tempInventory);
				
				// Break the block and replace it with the flowing type.
				CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, eventualBlock);
				
				didApply = true;
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
