package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionUseSelectedItemOnEntity implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_USE_SELECTED_ITEM_ON_ENTITY;

	public static Deprecated_EntityActionUseSelectedItemOnEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		int entityId = buffer.getInt();
		return new Deprecated_EntityActionUseSelectedItemOnEntity(entityId);
	}


	private final int _entityId;

	@Deprecated
	public Deprecated_EntityActionUseSelectedItemOnEntity(int entityId)
	{
		_entityId = entityId;
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
		buffer.putInt(_entityId);
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
		return "Use selected item on entity " + _entityId;
	}
}
