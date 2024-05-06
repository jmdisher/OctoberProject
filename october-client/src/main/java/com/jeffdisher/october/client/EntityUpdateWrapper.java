package com.jeffdisher.october.client;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * An adapter so that IEntityUpdate objects from the server can be fed in to the CrowdProcessor used in
 * SpeculativeProjection.
 */
public class EntityUpdateWrapper implements IMutationEntity<IMutablePlayerEntity>
{
	public final IEntityUpdate _update;

	public EntityUpdateWrapper(IEntityUpdate update)
	{
		_update = update;
	}

	@Override
	public long getTimeCostMillis()
	{
		// These don't go through a scheduler.
		throw Assert.unreachable();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// TODO:  We need to remove this down-cast once we split these for different entity types.
		_update.applyToEntity(context, (MutableEntity)newEntity);
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		// These are local-only.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// These are local-only.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// These are local-only.
		throw Assert.unreachable();
	}
}
