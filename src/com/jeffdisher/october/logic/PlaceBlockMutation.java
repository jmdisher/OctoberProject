package com.jeffdisher.october.logic;

import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;


public class PlaceBlockMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final Aspect<Short> _aspect;
	private final short _blockType;

	public PlaceBlockMutation(AbsoluteLocation location, Aspect<Short> aspect, short blockType)
	{
		_location = location;
		_aspect = aspect;
		_blockType = blockType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(WorldState oldWorld, CuboidData newCuboid, Consumer<IMutation> newMutationSink)
	{
		BlockAddress address = _location.getBlockAddress();
		short oldValue = newCuboid.getData15(_aspect, address);
		boolean didApply = false;
		if (0 == oldValue)
		{
			newCuboid.setData15(_aspect, address, _blockType);
			didApply = true;
		}
		return didApply;
	}
}
