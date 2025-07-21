package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This can no longer be a top-level action.
 */
public class Deprecated_EntityActionJump<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_JUMP;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityActionJump<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new Deprecated_EntityActionJump<>();
	}

	@Deprecated
	public Deprecated_EntityActionJump()
	{
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
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
		// There is nothing in this type.
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
		return "Jump";
	}
}
