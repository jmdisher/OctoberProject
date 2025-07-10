package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The current version of IEntityChange is just a stop-gap to allow updates to existing logic elsewhere and this class
 * is part of that stop-gap:  It just contains a mutation which it delivers to SpeculativeProjection.
 */
public class EntityChangeMutation implements IMutationEntity<IMutablePlayerEntity>
{
	private final IMutationBlock _contents;

	public EntityChangeMutation(IMutationBlock contents)
	{
		_contents = contents;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		if (null != _contents)
		{
			context.mutationSink.next(_contents);
		}
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		// NOTE:  This is only used in tests but is wrapped in EntityChangeTopLevelMovement so it needs a white-list
		// type.  This one is picked at random.
		return MutationEntityType.SELECT_ITEM;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
