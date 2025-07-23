package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


public class Deprecated_EntityActionChangeHotbarSlot implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_CHANGE_HOTBAR_SLOT;

	public static Deprecated_EntityActionChangeHotbarSlot deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int index = buffer.getInt();
		return new Deprecated_EntityActionChangeHotbarSlot(index);
	}


	private final int _index;

	@Deprecated
	public Deprecated_EntityActionChangeHotbarSlot(int index)
	{
		_index = index;
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
		buffer.putInt(_index);
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
		return "Select hotbar index " + _index;
	}
}
