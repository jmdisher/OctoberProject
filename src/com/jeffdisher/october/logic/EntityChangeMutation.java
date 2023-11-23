package com.jeffdisher.october.logic;

import java.util.function.Consumer;


/**
 * The current version of IEntityChange is just a stop-gap to allow updates to existing logic elsewhere and this class
 * is part of that stop-gap:  It just contains a mutation which it delivers to SpeculativeProjection.
 */
public class EntityChangeMutation implements IEntityChange
{
	private final IMutation _contents;
	private IMutation _reverseHolder;

	public EntityChangeMutation(IMutation contents)
	{
		_contents = contents;
	}

	@Override
	public boolean applyChange(Consumer<IMutation> newMutationSink)
	{
		if (null != _contents)
		{
			newMutationSink.accept(_contents);
		}
		return true;
	}

	@Override
	public IEntityChange applyChangeReversible(Consumer<IMutation> newMutationSink)
	{
		if (null != _contents)
		{
			newMutationSink.accept(_contents);
		}
		// The mutation we actually delivered is the one which changes things so we just create a null change which we can re-reverse, later.
		EntityChangeMutation reverse = new EntityChangeMutation(_reverseHolder);
		reverse._reverseHolder = _contents;
		return reverse;
	}
}
