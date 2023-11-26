package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutation;
import com.jeffdisher.october.mutations.NullMutation;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


public class ShockwaveMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final boolean _isStart;
	private final int _count;

	public ShockwaveMutation(AbsoluteLocation location, boolean isStart, int count)
	{
		_location = location;
		_isStart = isStart;
		_count = count;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		_commonMutation(context.newMutationSink);
		return true;
	}

	@Override
	public IMutation applyMutationReversible(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		_commonMutation(context.newMutationSink);
		// This mutation has no state change so just build a mutation which does the same thing, or nothing, depending on if this is where we started.
		return _isStart
				? new ShockwaveMutation(_location, _isStart, _count)
				: new NullMutation(_location)
		;
	}

	private void _commonMutation(Consumer<IMutation> newMutationSink)
	{
		if (_count > 0)
		{
			int thisCount = _count - 1;
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() - 1), false, thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() + 1), false, thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() - 1, _location.z()), false, thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() + 1, _location.z()), false, thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() - 1, _location.y(), _location.z()), false, thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() + 1, _location.y(), _location.z()), false, thisCount));
		}
	}
}
