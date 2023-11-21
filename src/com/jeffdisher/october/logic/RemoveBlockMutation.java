package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * The reverse counterpart to a PlaceBlockMutation.  It specifically replaces the block at this location and type with
 * air, failing if the block type was not what is expected.
 */
public class RemoveBlockMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final Aspect<Short> _aspect;
	private final short _blockType;

	public RemoveBlockMutation(AbsoluteLocation location, Aspect<Short> aspect, short blockType)
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
		if (_blockType == oldValue)
		{
			newBlock.setData15(_aspect, (short)0);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public IMutation applyMutationReversible(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		short oldValue = newBlock.getData15(_aspect);
		IMutation reverseMutation = null;
		if (_blockType == oldValue)
		{
			newBlock.setData15(_aspect, (short)0);
			// Reverse is to place this block type.
			reverseMutation = new PlaceBlockMutation(_location, _aspect, _blockType);
		}
		return reverseMutation;
	}
}
