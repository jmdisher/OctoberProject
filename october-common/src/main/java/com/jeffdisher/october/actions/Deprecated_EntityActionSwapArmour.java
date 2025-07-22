package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityActionSwapArmour implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_SWAP_ARMOUR;

	public static Deprecated_EntityActionSwapArmour deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		BodyPart slot = CodecHelpers.readBodyPart(buffer);
		int inventoryId = buffer.getInt();
		return new Deprecated_EntityActionSwapArmour(slot, inventoryId);
	}


	private final BodyPart _slot;
	private final int _inventoryId;

	@Deprecated
	public Deprecated_EntityActionSwapArmour(BodyPart slot, int inventoryId)
	{
		Assert.assertTrue(null != slot);
		
		_slot = slot;
		_inventoryId = inventoryId;
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
		CodecHelpers.writeBodyPart(buffer, _slot);
		buffer.putInt(_inventoryId);
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
		return "Swap armour in " + _slot + " with " + _inventoryId;
	}
}
