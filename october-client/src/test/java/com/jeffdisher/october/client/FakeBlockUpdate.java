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
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
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
				, null
				, new TickProcessingContext.IMutationSink()
				{
					@Override
					public void next(IMutationBlock mutation)
					{
					}
					@Override
					public void future(IMutationBlock mutation, long millisToDelay)
					{
						throw Assert.unreachable();
					}
				}
			, new TickProcessingContext.IChangeSink() {
				@Override
				public void next(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change)
				{
				}
				@Override
				public void future(int targetEntityId, IMutationEntity<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw Assert.unreachable();
				}
				@Override
				public void creature(int targetCreatureId, IMutationEntity<IMutableCreatureEntity> change)
				{
					throw Assert.unreachable();
				}
			}
			, null
			, null
			, new WorldConfig()
			, 100L
		);
		MutableBlockProxy mutable = new MutableBlockProxy(location, mutableData);
		boolean didApply = mutation.applyMutation(context, mutable);
		// This implementation needs to assume clean application.
		Assert.assertTrue(didApply);
		mutable.writeBack(mutableData);
		return MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(1024), mutable);
	}
}
