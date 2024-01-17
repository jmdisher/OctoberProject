package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


public class ShockwaveMutation implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final int _count;

	public ShockwaveMutation(AbsoluteLocation location, int count)
	{
		_location = location;
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

	private void _commonMutation(Consumer<IMutationBlock> newMutationSink)
	{
		if (_count > 0)
		{
			int thisCount = _count - 1;
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() - 1), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() + 1), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() - 1, _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() + 1, _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() - 1, _location.y(), _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() + 1, _location.y(), _location.z()), thisCount));
		}
	}
}
