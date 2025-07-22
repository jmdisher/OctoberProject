package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Sets the target block to the given logic state if it is sensitive to logic and isn't already in that state.
 * These mutations are created by MutationBlockLogicChange and can be applied to manual or automatic blocks, so long as
 * they are sinks.
 * Note that this mutation does not check what incoming signals are, assuming the signal level it is given should be
 * blindly applied.
 */
public class MutationBlockInternalSetLogicState implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.INTERNAL_SET_LOGIC_STATE;

	public static MutationBlockInternalSetLogicState deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		boolean setHigh = CodecHelpers.readBoolean(buffer);
		return new MutationBlockInternalSetLogicState(location, setHigh);
	}


	private final AbsoluteLocation _location;
	private final boolean _setHigh;

	public MutationBlockInternalSetLogicState(AbsoluteLocation location, boolean setHigh)
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
		
		// We assume that whoever scheduled us knows what they are doing and we only check the block type to make sure
		// it didn't change in the meantime.
		// If the block has a logic handler, its internal active state can be changed.
		Block previousBlock = newBlock.getBlock();
		LogicAspect.ISignalChangeCallback logicHandler = env.logic.logicUpdateHandler(previousBlock);
		if (null != logicHandler)
		{
			// As long as these are opposites, change to the alternative.
			byte flags = newBlock.getFlags();
			boolean isActive = FlagsAspect.isSet(flags, FlagsAspect.FLAG_ACTIVE);
			if (_setHigh != isActive)
			{
				flags = _setHigh
						? FlagsAspect.set(flags, FlagsAspect.FLAG_ACTIVE)
						: FlagsAspect.clear(flags, FlagsAspect.FLAG_ACTIVE)
				;
				newBlock.setFlags(flags);
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
