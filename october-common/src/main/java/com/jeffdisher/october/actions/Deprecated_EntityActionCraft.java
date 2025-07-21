package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionCraft implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_CRAFT;

	public static Deprecated_EntityActionCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		Craft operation = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new Deprecated_EntityActionCraft(operation);
	}


	private final Craft _operation;

	@Deprecated
	public Deprecated_EntityActionCraft(Craft operation)
	{
		// NOTE:  In storage version 6 or network version 8, this craft operation was not allowed to be null.
		// In storage 7 and network 9, this was relaxed so that it can be null.
		_operation = operation;
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
		CodecHelpers.writeCraft(buffer, _operation);
		buffer.putLong(0L); // millis no longer stored.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Craft " + _operation;
	}
}
