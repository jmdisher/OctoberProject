package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;


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
	public boolean applyMutation(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		short oldValue = newBlock.getData15(_aspect);
		boolean didApply = false;
		if (0 == oldValue)
		{
			newBlock.setData15(_aspect, _blockType);
			didApply = true;
		}
		return didApply;
	}
}
