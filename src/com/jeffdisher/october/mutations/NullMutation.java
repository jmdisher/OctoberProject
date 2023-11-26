package com.jeffdisher.october.mutations;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A mutation which does nothing.  This is used in cases where a reverse mutation for a secondary mutation which had no
 * impact is required to signify success.
 */
public class NullMutation implements IMutation
{
	private final AbsoluteLocation _location;

	public NullMutation(AbsoluteLocation location)
	{
		_location = location;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		return true;
	}

	@Override
	public IMutation applyMutationReversible(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		return this;
	}
}
