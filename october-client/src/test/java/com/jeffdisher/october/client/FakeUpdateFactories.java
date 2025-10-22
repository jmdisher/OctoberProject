package com.jeffdisher.october.client;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.TickUtils;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.IPassiveAction;
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
		// We need to call didChange() to clear redundant changes.
		if (mutable.didChange())
		{
			mutable.writeBack(mutableData);
		}
		return MutationBlockSetBlock.extractFromProxy(ByteBuffer.allocate(1024), mutable);
	}

	public static MutationEntitySetEntity entityUpdate(Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids, Entity entity, IEntityAction<IMutablePlayerEntity> mutation)
	{
		TickProcessingContext context = _createFakeContext(loadedCuboids);
		
		MutableEntity newEntity = MutableEntity.existing(entity);
		mutation.applyChange(context, newEntity);
		// We also need the corresponding end of tick.
		if (TickUtils.canApplyEnvironmentalDamageInTick(context))
		{
			TickUtils.applyEnvironmentalDamage(context, newEntity);
		}
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
				, null
				, new TickProcessingContext.IMutationSink()
				{
					@Override
					public boolean next(IMutationBlock mutation)
					{
						return false;
					}
					@Override
					public boolean future(IMutationBlock mutation, long millisToDelay)
					{
						throw Assert.unreachable();
					}
				}
			, new TickProcessingContext.IChangeSink() {
				@Override
				public boolean next(int targetEntityId, IEntityAction<IMutablePlayerEntity> change)
				{
					return false;
				}
				@Override
				public boolean future(int targetEntityId, IEntityAction<IMutablePlayerEntity> change, long millisToDelay)
				{
					throw Assert.unreachable();
				}
				@Override
				public boolean creature(int targetCreatureId, IEntityAction<IMutableCreatureEntity> change)
				{
					throw Assert.unreachable();
				}
				@Override
				public boolean passive(int targetPassiveId, IPassiveAction action)
				{
					throw Assert.unreachable();
				}
			}
			, null
			, null
			// We will just use 0 as a fixed random value.
			, (int limit) -> 0
			, (EventRecord record) -> {}
			, (CuboidAddress address) -> {}
			, new WorldConfig()
			, 100L
			, 0L
		);
		return context;
	}
}
