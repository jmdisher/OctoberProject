package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionSwim<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SWIM;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityActionSwim<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new Deprecated_EntityActionSwim<>();
	}

	@Deprecated
	public Deprecated_EntityActionSwim()
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
		return "Swim";
	}
}
