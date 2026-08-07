package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Replaces the block at the location with the newType, assuming it is a non-solid type.  If the block is solid, then
 * the newType is dropped as a passive on top of it.
 * If the block is not solid, but not replaceable (a sapling, for example), then the block is replaced with newType and
 * the old type is dropped as a passive on top of it.
 * This mutation is commonly used by FALLING_BLOCK passives when converting back into a solid block.
 */
public class MutationBlockReplaceDropExisting implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.REPLACE_DROP_EXISTING;

	public static MutationBlockReplaceDropExisting deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block newType = CodecHelpers.readBlock(buffer);
		return new MutationBlockReplaceDropExisting(location,  newType);
	}


	private final AbsoluteLocation _location;
	private final Block _newType;

	public MutationBlockReplaceDropExisting(AbsoluteLocation location, Block newType)
	{
		_location = location;
		_newType = newType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		
		// Check if the existing block is solid.
		Block oldType = newBlock.getBlock();
		boolean isActive = FlagsAspect.isSet(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
		AbsoluteLocation passiveDropBlock = _location.getRelative(0, 0, 1);
		if (env.blocks.isSolid(oldType, isActive))
		{
			// This is solid so we won't replace it.  Drop the input as a passive on top.
			CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, passiveDropBlock, _newType);
		}
		else
		{
			// We will replace this but check to see what the original block drops, first.
			CommonBlockMutationHelpers.dropAsPassivesWhenBreakingBlock(env, context, passiveDropBlock, oldType);
			CommonBlockMutationHelpers.dropBlockInventoriesAsPassives(context, passiveDropBlock, newBlock);
			
			// Overwrite the block with the new type.
			newBlock.setBlockAndClear(_newType);
			
			// Handle the case where this is a gravity block.
			if (env.blocks.hasGravity(_newType))
			{
				// If we think that this should fall, schedule the apply gravity mutation.
				BlockProxy belowBlock = context.previousBlockLookUp.readBlock(_location.getRelative(0, 0, -1));
				if (null != belowBlock)
				{
					if (!env.blocks.isSupportedAgainstGravity(_newType, belowBlock.getBlock()))
					{
						context.mutationSink.next(new MutationBlockApplyGravity(_location));
					}
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeBlock(buffer, _newType);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
