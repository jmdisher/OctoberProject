package com.jeffdisher.october.logic;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;


public class PlaceBlockMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final short _blockType;

	public PlaceBlockMutation(AbsoluteLocation location, short blockType)
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
		if (BlockAspect.AIR == oldValue)
		{
			newBlock.setData15(AspectRegistry.BLOCK, _blockType);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public IMutation applyMutationReversible(Function<AbsoluteLocation, BlockProxy> oldWorldLoader, MutableBlockProxy newBlock, Consumer<IMutation> newMutationSink)
	{
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		IMutation reverseMutation = null;
		if (BlockAspect.AIR == oldValue)
		{
			newBlock.setData15(AspectRegistry.BLOCK, _blockType);
			// Reverse is to remove this block type.
			reverseMutation = new RemoveBlockMutation(_location, _blockType);
		}
		return reverseMutation;
	}
}
