package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * A helper class to quickly fake-up MutationBlockSetBlock of MutationEntitySetEntity instances based on applying single
 * mutations to existing blocks or entities.
 */
public class FakeUpdateFactories
{
	public static MutationBlockSetBlock blockUpdate(CuboidData mutableData, IMutationBlock mutation)
	{
		TickProcessingContext context = _createFakeContext(Map.of(mutableData.getCuboidAddress(), mutableData));
		
		AbsoluteLocation location = mutation.getAbsoluteLocation();
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		Assert.assertTrue(null != proxy);
		
		MutableBlockProxy mutable = new MutableBlockProxy(location, mutableData);
		boolean didApply = mutation.applyMutation(context, mutable);
		// This implementation needs to assume clean application.
		Assert.assertTrue(didApply);
		mutable.writeBack(mutableData);
		return MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(1024), mutable);
	}

	public static MutationEntitySetEntity entityUpdate(Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids, Entity entity, IMutationEntity<IMutablePlayerEntity> mutation)
	{
		TickProcessingContext context = _createFakeContext(loadedCuboids);
		
		MutableEntity newEntity = MutableEntity.existing(entity);
		mutation.applyChange(context, newEntity);
		// We also need the corresponding end of tick.
		long millisInChange = mutation.getTimeCostMillis();
		if (millisInChange > 0L)
		{
			TickUtils.allowMovement(context.previousBlockLookUp, null, newEntity, millisInChange);
		}
		TickUtils.endOfTick(context, newEntity);
		return new MutationEntitySetEntity(newEntity.freeze());
	}


	private static TickProcessingContext _createFakeContext(Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids)
	{
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) ->
		{
			IReadOnlyCuboidData data = loadedCuboids.get(location.getCuboidAddress());
			return (null != data)
					? new BlockProxy(location.getBlockAddress(), data)
					: null
			;
		};
		
		// We will just fake up the tick context to be benign (since the speculative projection will drop any new mutations) and only fail when it can't know the answer.
		TickProcessingContext context = new TickProcessingContext(0L
				, previousBlockLookUp
				, null
				, null
				, new TickProcessingContext.IMutationSink()
				{
					@Override
					public void next(IMutationBlock mutation)
					{
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
			, 0L
		);
		return context;
	}
}
