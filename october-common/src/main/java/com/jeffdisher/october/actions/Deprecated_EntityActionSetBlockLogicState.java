package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionSetBlockLogicState implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SET_BLOCK_LOGIC_STATE;

	public static Deprecated_EntityActionSetBlockLogicState deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		boolean setHigh = CodecHelpers.readBoolean(buffer);
		return new Deprecated_EntityActionSetBlockLogicState(target, setHigh);
	}


	private final AbsoluteLocation _targetBlock;
	private final boolean _setHigh;

	@Deprecated
	public Deprecated_EntityActionSetBlockLogicState(AbsoluteLocation targetBlock, boolean setHigh)
	{
		_targetBlock = targetBlock;
		_setHigh = setHigh;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Not used.
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _targetBlock);
		CodecHelpers.writeBoolean(buffer, _setHigh);
	}

	@Override
	public boolean canSaveToDisk()
	{
		return true;
	}

	@Override
	public String toString()
	{
		return "Set logic state of " + _targetBlock + " to " + (_setHigh ? "HIGH" : "LOW");
	}
}
