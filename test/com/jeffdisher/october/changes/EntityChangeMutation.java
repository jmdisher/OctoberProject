package com.jeffdisher.october.changes;

import java.util.function.Consumer;

import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.types.MutableEntity;


/**
 * The current version of IEntityChange is just a stop-gap to allow updates to existing logic elsewhere and this class
 * is part of that stop-gap:  It just contains a mutation which it delivers to SpeculativeProjection.
 */
public class EntityChangeMutation implements IEntityChange
{
	private final int _entityId;
	private final IMutation _contents;
	private IMutation _reverseHolder;

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
	public boolean applyChange(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
	{
		if (null != _contents)
		{
			newMutationSink.accept(_contents);
		}
		return true;
	}

	@Override
	public IEntityChange applyChangeReversible(MutableEntity newEntity, Consumer<IMutation> newMutationSink, Consumer<IEntityChange> newChangeSink)
	{
		if (null != _contents)
		{
			newMutationSink.accept(_contents);
		}
		// The mutation we actually delivered is the one which changes things so we just create a null change which we can re-reverse, later.
		EntityChangeMutation reverse = new EntityChangeMutation(_entityId, _reverseHolder);
		reverse._reverseHolder = _contents;
		return reverse;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// We don't merge these.
		return false;
	}
}
