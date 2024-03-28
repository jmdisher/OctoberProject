package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is mostly just for testing purposes but allows us to write changes to block types in a reversible way.
 * It is just a way to replace a specific block type instance with a new block type instance.
 * NOTE:  This ignores all other aspects of the block, which could cause confusion if it were to be used in a real
 * system (it won't delete inventories, for example).
 */
public class ReplaceBlockMutation implements IMutationBlock
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
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		if (_oldType == newBlock.getBlock().item().number())
		{
			newBlock.setBlockAndClear(BlockAspect.BLOCKS_BY_TYPE[_newType]);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
