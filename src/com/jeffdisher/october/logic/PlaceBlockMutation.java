package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.utils.Encoding;


public class PlaceBlockMutation implements IMutation
{
	private final int _absoluteX;
	private final int _absoluteY;
	private final int _absoluteZ;
	private final Aspect<Short> _aspect;
	private final short _blockType;

	public PlaceBlockMutation(int absoluteX, int absoluteY, int absoluteZ, Aspect<Short> aspect, short blockType)
	{
		_absoluteX = absoluteX;
		_absoluteY = absoluteY;
		_absoluteZ = absoluteZ;
		_aspect = aspect;
		_blockType = blockType;
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
		byte x = Encoding.getBlockAddress(_absoluteX);
		byte y = Encoding.getBlockAddress(_absoluteY);
		byte z = Encoding.getBlockAddress(_absoluteZ);
		short oldValue = newCuboid.getData15(_aspect, x, y, z);
		boolean didApply = false;
		if (0 == oldValue)
		{
			newCuboid.setData15(_aspect, x, y, z, _blockType);
			didApply = true;
		}
		return didApply;
	}
}
