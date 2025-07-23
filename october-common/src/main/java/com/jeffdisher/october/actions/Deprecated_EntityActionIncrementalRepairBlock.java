package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionIncrementalRepairBlock implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_INCREMENTAL_REPAIR_BLOCK;

	public static Deprecated_EntityActionIncrementalRepairBlock deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		buffer.getShort();
		return new Deprecated_EntityActionIncrementalRepairBlock(target);
	}


	private final AbsoluteLocation _targetBlock;

	@Deprecated
	public Deprecated_EntityActionIncrementalRepairBlock(AbsoluteLocation targetBlock)
	{
		_targetBlock = targetBlock;
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
		buffer.putShort((short)0); // millis no longer stored.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// The target may have changed.
		return false;
	}

	@Override
	public String toString()
	{
		return "Incremental break " + _targetBlock;
	}
}
