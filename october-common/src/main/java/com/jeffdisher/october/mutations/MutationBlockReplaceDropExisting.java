package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.MutableInventory;
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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		MutableInventory tempInventory = new MutableInventory(Inventory.start(Integer.MAX_VALUE).finish());
		
		// Check if the existing block is solid.
		Block oldType = newBlock.getBlock();
		boolean isActive = FlagsAspect.isSet(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
		if (env.blocks.isSolid(oldType, isActive))
		{
			// This is solid so we won't replace it.  Drop the input as a passive on top.
			CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, tempInventory, _newType);
		}
		else
		{
			// We will replace this but check to see what the original block drops, first.
			CommonBlockMutationHelpers.populateInventoryWhenBreakingBlock(env, context, tempInventory, oldType);
			CommonBlockMutationHelpers.fillInventoryFromBlockWithoutLimit(tempInventory, newBlock);
			
			// Overwrite the block with the new type.
			newBlock.setBlockAndClear(_newType);
		}
		
		// Drop anything which needs to be put somewhere.
		if (tempInventory.getCurrentEncumbrance() > 0)
		{
			AbsoluteLocation passiveDropBlock = _location.getRelative(0, 0, 1);
			CommonBlockMutationHelpers.dropTempInventoryAsPassives(context, passiveDropBlock, tempInventory);
		}
		
		// In either case, we took some action so say this applied.
		return true;
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
