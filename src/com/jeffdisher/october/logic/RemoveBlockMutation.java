package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * The reverse counterpart to a PlaceBlockMutation.  It specifically replaces the block at this location and type with
 * air, failing if the block type was not what is expected.
 */
public class RemoveBlockMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final short _blockType;

	public RemoveBlockMutation(AbsoluteLocation location, short blockType)
	{
		_location = location;
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
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		boolean didApply = false;
		if (_blockType == oldValue)
		{
			newBlock.setData15(AspectRegistry.BLOCK, BlockAspect.AIR);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public IMutation applyMutationReversible(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		IMutation reverseMutation = null;
		if (_blockType == oldValue)
		{
			newBlock.setData15(AspectRegistry.BLOCK, BlockAspect.AIR);
			// Reverse is to place this block type.
			reverseMutation = new PlaceBlockMutation(_location, _blockType);
		}
		return reverseMutation;
	}
}
