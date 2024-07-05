package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * We wrap an IMutationEntity in IEntityUpdate for unit tests.
 */
public class EntityMutationWrapper implements IEntityUpdate
{
	private final IMutationEntity<IMutablePlayerEntity> _mutation;

	public EntityMutationWrapper(IMutationEntity<IMutablePlayerEntity> mutation)
	{
		_mutation = mutation;
	}

	@Override
	public void applyToEntity(TickProcessingContext context, MutableEntity newEntity)
	{
		// This is only used for client-side unit tests which use a 0ms tick time for updates from server so we will override this context.
		// Ideally, tests should stop using this utility, altogether, since it is an ugly hack.
		long millisPerTick = 100L;
		TickProcessingContext override = new TickProcessingContext(context.currentTick
				, context.previousBlockLookUp
				, context.previousEntityLookUp
				, context.mutationSink
				, context.newChangeSink
				, context.idAssigner
				, context.randomInt
				, context.difficulty
				, millisPerTick
		);
		_mutation.applyChange(override, newEntity);
		// We also need the corresponding end of tick.
		new EntityEndOfTick(millisPerTick).apply(override, newEntity);
	}

	@Override
	public EntityUpdateType getType()
	{
		// Not in test.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		// Not in test.
		throw Assert.unreachable();
	}
}
