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
 * A mutation to replace a block with the given data.  This is for use in purely-internal cases where there isn't a need
 * (either ever, or at least currently) to persist the mutation.
 * The reason for this is that it creates a cheap mutation which can easily have data added or removed as there is NO
 * DISK REPRESENTATION.
 */
public class MutationBlockOverwriteMisc implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final Block _blockType;
	private final FacingDirection _outputDirection;
	private final byte _blockDefined;

	public MutationBlockOverwriteMisc(AbsoluteLocation location
		, Block blockType
		, FacingDirection outputDirection
		, byte blockDefined
	)
	{
		_location = location;
		_blockType = blockType;
		_outputDirection = outputDirection;
		_blockDefined = blockDefined;
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
		CommonBlockMutationHelpers.overwriteBlockIfReplaceableWithFollowUps(context, newBlock, _location, _blockType, _outputDirection, _blockDefined, false);
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
		// It is possible that a write will be attempted but we will just drop this, in that case.
		// In the future, we may want to persist this if the included aspect data is solidified (as leaving this easy to
		// update is the main reason not to persist).
		return false;
	}
}
