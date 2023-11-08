package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.data.CuboidData;


public class ShockwaveMutation implements IMutation
{
	private final int _absoluteX;
	private final int _absoluteY;
	private final int _absoluteZ;
	private final int _count;

	public ShockwaveMutation(int absoluteX, int absoluteY, int absoluteZ, int count)
	{
		_absoluteX = absoluteX;
		_absoluteY = absoluteY;
		_absoluteZ = absoluteZ;
		_count = count;
	}

	@Override
	public int[] getAbsoluteLocation()
	{
		return new int[] {_absoluteX
				, _absoluteY
				, _absoluteZ
		};
	}

	@Override
	public boolean applyMutation(WorldState oldWorld, CuboidData newCuboid, Consumer<IMutation> newMutationSink)
	{
		if (_count > 0)
		{
			int thisCount = _count - 1;
			newMutationSink.accept(new ShockwaveMutation(_absoluteX, _absoluteY, _absoluteZ - 1, thisCount));
			newMutationSink.accept(new ShockwaveMutation(_absoluteX, _absoluteY, _absoluteZ + 1, thisCount));
			newMutationSink.accept(new ShockwaveMutation(_absoluteX, _absoluteY - 1, _absoluteZ, thisCount));
			newMutationSink.accept(new ShockwaveMutation(_absoluteX, _absoluteY + 1, _absoluteZ, thisCount));
			newMutationSink.accept(new ShockwaveMutation(_absoluteX - 1, _absoluteY, _absoluteZ, thisCount));
			newMutationSink.accept(new ShockwaveMutation(_absoluteX + 1, _absoluteY, _absoluteZ, thisCount));
		}
		return true;
	}
}
