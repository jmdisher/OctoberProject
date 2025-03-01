package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Sets the target block to the given logic state if it is sensitive to logic, allows manual updates, and isn't already
 * in that state.
 * These mutations are created by EntityChangeSetBlockLogicState
 */
public class MutationBlockSetLogicState implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.SET_LOGIC_STATE;

	public static MutationBlockSetLogicState deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		boolean setHigh = CodecHelpers.readBoolean(buffer);
		return new MutationBlockSetLogicState(location, setHigh);
	}


	private final AbsoluteLocation _location;
	private final boolean _setHigh;

	public MutationBlockSetLogicState(AbsoluteLocation location, boolean setHigh)
	{
		_location = location;
		_setHigh = setHigh;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		Block previousBlock = newBlock.getBlock();
		if (env.logic.isManual(previousBlock))
		{
			// As long as these are opposites, change to the alternative.
			boolean areOpposite = _setHigh == !env.logic.isHigh(previousBlock);
			if (areOpposite)
			{
				Block alternate = env.logic.getAlternate(previousBlock);
				newBlock.setBlockAndClear(alternate);
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeBoolean(buffer, _setHigh);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
