package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionUseSelectedItemOnSelf implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_USE_SELECTED_ITEM_ON_SELF;

	public static Deprecated_EntityActionUseSelectedItemOnSelf deserializeFromBuffer(ByteBuffer buffer)
	{
		return new Deprecated_EntityActionUseSelectedItemOnSelf();
	}


	@Deprecated
	public Deprecated_EntityActionUseSelectedItemOnSelf()
	{
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
		return "Use selected item on self";
	}
}
