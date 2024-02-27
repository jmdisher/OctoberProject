package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
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
		Item blockType = CodecHelpers.readItem(buffer);
		return new MutationBlockOverwrite(location, blockType);
	}


	private final AbsoluteLocation _location;
	private final Item _blockType;

	public MutationBlockOverwrite(AbsoluteLocation location, Item blockType)
	{
		// Using this with AIR doesn't make sense.
		Assert.assertTrue(ItemRegistry.AIR != blockType);
		
		_location = location;
		_blockType = blockType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		boolean didApply = false;
		// Check to see if this is the expected type.
		if (ItemRegistry.AIR == newBlock.getItem())
		{
			// Replace the block with the type we have.
			newBlock.setItemAndClear(_blockType);
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
		CodecHelpers.writeItem(buffer, _blockType);
	}
}
