package com.jeffdisher.october.changes;

import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * The current version of IEntityChange is just a stop-gap to allow updates to existing logic elsewhere and this class
 * is part of that stop-gap:  It just contains a mutation which it delivers to SpeculativeProjection.
 */
public class EntityChangeMutation implements IEntityChange
{
	private final IMutation _contents;

	public EntityChangeMutation(IMutation contents)
	{
		_contents = contents;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		if (null != _contents)
		{
			context.newMutationSink.accept(_contents);
		}
		return true;
	}
}
