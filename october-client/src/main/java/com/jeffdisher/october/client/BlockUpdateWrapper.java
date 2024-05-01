package com.jeffdisher.october.client;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An adapter to allow IBlockStateUpdate to be passed into common code expecting IMutationBlock.
 */
public class BlockUpdateWrapper implements IMutationBlock
{
	private final MutationBlockSetBlock _stateUpdate;

	public BlockUpdateWrapper(MutationBlockSetBlock stateUpdate)
	{
		_stateUpdate = stateUpdate;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _stateUpdate.getAbsoluteLocation();
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		_stateUpdate.applyState(newBlock);
		return true;
	}

	@Override
	public MutationBlockType getType()
	{
		// Shouldn't be called since this is only client-side.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Shouldn't be called since this is only client-side.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Shouldn't be called since this is only client-side.
		throw Assert.unreachable();
	}
}
