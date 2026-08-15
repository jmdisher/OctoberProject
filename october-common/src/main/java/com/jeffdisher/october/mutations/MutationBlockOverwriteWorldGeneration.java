package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IMutableBlockProxy;
import com.jeffdisher.october.types.IMutationBlock;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A mutation to replace a block with the given data.  This is used explicitly by the world generator and is not
 * persistent so that new data can be added for other aspects, as needed.  Since this should be run immediately after
 * generation, there should never be a need to serialize this, anyway.
 */
public class MutationBlockOverwriteWorldGeneration implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final Block _blockType;
	private final FacingDirection _outputDirection;

	public MutationBlockOverwriteWorldGeneration(AbsoluteLocation location
		, Block blockType
		, FacingDirection outputDirection
	)
	{
		_location = location;
		_blockType = blockType;
		_outputDirection = outputDirection;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// We ignore the return value of whether or not the change applied.
		CommonBlockMutationHelpers.overwriteBlockIfReplaceableWithFollowUps(context, newBlock, _location, _outputDirection, _blockType, false);
	}

	@Override
	public MutationBlockType getType()
	{
		// No serialization.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// No serialization.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// We don't expect this kind of mutation to ever even be considered for serialization.
		throw Assert.unreachable();
	}
}
