package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.PlantRegistry;
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
		Block blockType = BlockAspect.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		return new MutationBlockOverwrite(location, blockType);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;

	public MutationBlockOverwrite(AbsoluteLocation location, Block blockType)
	{
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(!BlockAspect.canBeReplaced(blockType));
		
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
		if (BlockAspect.canBeReplaced(newBlock.getBlock()))
		{
			// Make sure that this block can be supported by the one under it.
			boolean blockIsSupported = BlockAspect.canExistOnBlock(_blockType, context.previousBlockLookUp.apply(_location.getRelative(0, 0, -1)).getBlock());
			
			// Note that failing to place this means that the block will be destroyed.
			if (blockIsSupported)
			{
				// Replace the block with the type we have.
				newBlock.setBlockAndClear(_blockType);
				
				if (PlantRegistry.growthDivisor(_blockType) > 0)
				{
					context.delatedMutationSink.accept(new MutationBlockGrow(_location), MutationBlockGrow.MILLIS_BETWEEN_GROWTH_CALLS);
				}
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeItem(buffer, _blockType.asItem());
	}
}
