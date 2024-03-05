package com.jeffdisher.october.client;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.BlockStateUpdateType;
import com.jeffdisher.october.mutations.IBlockStateUpdate;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This just directly wraps an IMutationBlock instance to make testing easier.  This isn't normally an appropriate use
 * of this interface, however, so should only be used in tests.
 */
public class FakeBlockUpdate implements IBlockStateUpdate
{
	private final IMutationBlock _mutation;

	public FakeBlockUpdate(IMutationBlock mutation)
	{
		_mutation = mutation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _mutation.getAbsoluteLocation();
	}

	@Override
	public void applyState(IMutableBlockProxy newBlock)
	{
		// We will just fake up the tick context to be benign (since the speculative projection will drop any new mutations) and only fail when it can't know the answer.
		TickProcessingContext context = new TickProcessingContext(0L
				, null
				, (IMutationBlock newMutation) -> {}
				, (int targetEntityId, IMutationEntity change) -> {}
		);
		boolean didApply = _mutation.applyMutation(context, newBlock);
		// This implementation needs to assume clean application.
		Assert.assertTrue(didApply);
	}

	@Override
	public BlockStateUpdateType getType()
	{
		// Not serialized.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not serialized.
		throw Assert.unreachable();
	}
}
