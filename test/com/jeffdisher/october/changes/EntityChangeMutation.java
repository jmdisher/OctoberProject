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
	private final int _entityId;
	private final IMutation _contents;

	public EntityChangeMutation(int entityId, IMutation contents)
	{
		_entityId = entityId;
		_contents = contents;
	}

	@Override
	public int getTargetId()
	{
		return _entityId;
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

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// We don't merge these.
		return false;
	}
}
