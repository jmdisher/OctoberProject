package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Inventory;
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
		Environment env = Environment.getShared();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		Block blockType = env.blocks.getAsPlaceableBlock(CodecHelpers.readItem(buffer));
		return new MutationBlockOverwrite(location, blockType);
	}


	private final AbsoluteLocation _location;
	private final Block _blockType;

	public MutationBlockOverwrite(AbsoluteLocation location, Block blockType)
	{
		Environment env = Environment.getShared();
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(!env.blocks.canBeReplaced(blockType));
		
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
		Environment env = Environment.getShared();
		boolean didApply = false;
		// Check to see if this is the expected type.
		Block oldBlock = newBlock.getBlock();
		if (env.blocks.canBeReplaced(oldBlock))
		{
			// See if the block we are changing needs a special logic mode.
			Block newType = LogicLayerHelpers.blockTypeToPlace(context, _location, _blockType);
			
			// Make sure that this block can be supported by the one under it.
			BlockProxy belowBlock = context.previousBlockLookUp.apply(_location.getRelative(0, 0, -1));
			// If the cuboid beneath this isn't loaded, we will just treat it as supported (best we can do in this situation).
			boolean blockIsSupported = (null != belowBlock)
					? env.blocks.canExistOnBlock(newType, belowBlock.getBlock())
					: true
			;
			
			// Note that failing to place this means that the block will be destroyed.
			if (blockIsSupported)
			{
				// If we are placing a block which allows entity movement, be sure to copy over any inventory on the ground.
				Inventory inventoryToRestore = !env.blocks.isSolid(newType)
						? newBlock.getInventory()
						: null
				;
				
				// Replace the block with the type we have.
				newBlock.setBlockAndClear(newType);
				if (null != inventoryToRestore)
				{
					newBlock.setInventory(inventoryToRestore);
				}
				
				if (env.plants.growthDivisor(newType) > 0)
				{
					context.mutationSink.future(new MutationBlockGrow(_location), MutationBlockGrow.MILLIS_BETWEEN_GROWTH_CALLS);
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
		CodecHelpers.writeItem(buffer, _blockType.item());
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
