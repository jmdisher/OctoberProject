package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Over-writes a the given block if it is AIR, also destroying any inventory it might have had.
 * Upon failure, no retry is attempted so the block being placed is lost.
 */
public class MutationBlockOverwrite implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.OVERWRITE_BLOCK;

	public static MutationBlockOverwrite deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block blockType = BlockAspect.getBlock(CodecHelpers.readItem(buffer));
		return new MutationBlockOverwrite(location, blockType);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;

	public MutationBlockOverwrite(AbsoluteLocation location, Block blockType)
	{
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(!blockType.canBeReplaced());
		
		_location = location;
		_blockType = blockType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		// Check to see if this is the expected type.
		if (newBlock.getBlock().canBeReplaced())
		{
			// Replace the block with the type we have.
			newBlock.setBlockAndClear(_blockType);
			
			// TODO:  Find some good way to generalize the need for "growth" behaviour.
			if (ItemRegistry.SAPLING == _blockType.asItem())
			{
				context.delatedMutationSink.accept(new MutationBlockGrow(_location), MutationBlockGrow.MILLIS_BETWEEN_GROWTH_CALLS);
			}
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeItem(buffer, _blockType.asItem());
	}
}
