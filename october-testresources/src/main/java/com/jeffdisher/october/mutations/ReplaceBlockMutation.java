package com.jeffdisher.october.mutations;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.registries.AspectRegistry;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is mostly just for testing purposes but allows us to write changes to block types in a reversible way.
 * It is just a way to replace a specific block type instance with a new block type instance.
 * NOTE:  This ignores all other aspects of the block, which could cause confusion if it were to be used in a real
 * system (it won't delete inventories, for example).
 */
public class ReplaceBlockMutation implements IMutation
{
	private final AbsoluteLocation _location;
	private final short _oldType;
	private final short _newType;

	public ReplaceBlockMutation(AbsoluteLocation location, short oldType, short newType)
	{
		_location = location;
		_oldType = oldType;
		_newType = newType;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		short oldValue = newBlock.getData15(AspectRegistry.BLOCK);
		boolean didApply = false;
		if (_oldType == oldValue)
		{
			newBlock.setData15(AspectRegistry.BLOCK, _newType);
			didApply = true;
		}
		return didApply;
	}
}
