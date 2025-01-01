package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
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

	public static MutationBlockLiquidFlowInto deserializeFromBuffer(ByteBuffer buffer)
	{
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
		// Liquids only flow in blocks which can be replaced.
		Block thisBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(thisBlock))
		{
			// For now, there are no interactions with source blocks.
			if (!env.liquids.isSource(thisBlock))
			{
				Block newType = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation);
				if (newType != thisBlock)
				{
					Inventory inv = CommonBlockMutationHelpers.replaceBlockAndRestoreInventory(env, newBlock, newType);
					// The inventory should be restored if these are all empty block types.
					Assert.assertTrue(null == inv);
					thisBlock = newType;
					didApply = true;
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
