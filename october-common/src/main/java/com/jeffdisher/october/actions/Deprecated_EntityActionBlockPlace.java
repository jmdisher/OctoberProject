package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionBlockPlace implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_BLOCK_PLACE;

	public static Deprecated_EntityActionBlockPlace deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		AbsoluteLocation blockOutput = CodecHelpers.readAbsoluteLocation(buffer);
		return new Deprecated_EntityActionBlockPlace(target, blockOutput);
	}


	private final AbsoluteLocation _targetBlock;
	private final AbsoluteLocation _blockOutput;

	@Deprecated
	public Deprecated_EntityActionBlockPlace(AbsoluteLocation targetBlock, AbsoluteLocation blockOutput)
	{
		_targetBlock = targetBlock;
		_blockOutput = blockOutput;
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
		CodecHelpers.writeAbsoluteLocation(buffer, _blockOutput);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Block reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Place selected block " + _targetBlock + " facing " + _blockOutput;
	}
}
