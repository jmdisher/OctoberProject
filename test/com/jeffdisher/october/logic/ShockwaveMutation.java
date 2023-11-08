package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;


public class ShockwaveMutation implements IMutation
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
	public boolean applyMutation(WorldState oldWorld, CuboidData newCuboid, Consumer<IMutation> newMutationSink)
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
		return true;
	}
}
