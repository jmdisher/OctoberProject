package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A helper class to quickly fake-up MutationBlockSetBlock instances based on applying a single mutation to an existing
 * block.
 */
public class FakeBlockUpdate
{
	public static MutationBlockSetBlock applyUpdate(CuboidData mutableData, IMutationBlock mutation)
	{
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) ->
		{
			return mutableData.getCuboidAddress().equals(location.getCuboidAddress())
					? new BlockProxy(location.getBlockAddress(), mutableData)
					: null
			;
		};
		
		AbsoluteLocation location = mutation.getAbsoluteLocation();
		BlockProxy proxy = previousBlockLookUp.apply(location);
		Assert.assertTrue(null != proxy);
		
		// We will just fake up the tick context to be benign (since the speculative projection will drop any new mutations) and only fail when it can't know the answer.
		TickProcessingContext context = new TickProcessingContext(0L
				, previousBlockLookUp
				, (IMutationBlock newMutation) -> {}
				, (int targetEntityId, IMutationEntity change) -> {}
		);
		MutableBlockProxy mutable = new MutableBlockProxy(location, mutableData);
		boolean didApply = mutation.applyMutation(context, mutable);
		// This implementation needs to assume clean application.
		Assert.assertTrue(didApply);
		mutable.writeBack(mutableData);
		return MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(1024), mutable);
	}
}
