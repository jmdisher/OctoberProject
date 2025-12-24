package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The generic placeholder for all deprecated IEntityAction instances.  We don't want to preserve deprecated behaviour,
 * just decode the data stream correctly, meaning that all of these can use the same do-nothing type.
 * 
 * @param <T> The entity type.
 */
public class Deprecated_EntityAction<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_Move(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		buffer.getLong();
		float speedMultiplier = buffer.getFloat();
		byte directionOrdinal = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_Jump(DeserializationContext context)
	{
		return new Deprecated_EntityAction<>();
	}

	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_Swim(DeserializationContext context)
	{
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_BlockPlace(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		AbsoluteLocation blockOutput = CodecHelpers.readAbsoluteLocation(buffer);
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_Craft(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		Craft operation = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_SelectItem(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int inventoryId = buffer.getInt();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_ItemsRequestPush(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		int localInventoryId = buffer.getInt();
		Assert.assertTrue(localInventoryId > 0);
		int count = buffer.getInt();
		Assert.assertTrue(count > 0);
		byte inventoryAspect = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_ItemsRequestPull(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		int blockInventoryKey = buffer.getInt();
		int countRequested = buffer.getInt();
		byte inventoryAspect = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_IncrementalBreakBlock(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		buffer.getShort();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_CraftInBlock(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation targetBlock = CodecHelpers.readAbsoluteLocation(buffer);
		Craft craft = CodecHelpers.readCraft(buffer);
		buffer.getLong();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_AttackEntity(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int targetEntityId = buffer.getInt();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_TakeDamageV2(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		byte damage = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_UseSelectedItemOnSelf(DeserializationContext context)
	{
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_UseSelectedItemOnBlock(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_UseSelectedItemOnEntity(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int entityId = buffer.getInt();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_ChangeHotbarSlot(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int index = buffer.getInt();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_SwapArmour(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		BodyPart slot = CodecHelpers.readBodyPart(buffer);
		int inventoryId = buffer.getInt();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_SetBlockLogicState(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		boolean setHigh = CodecHelpers.readBoolean(buffer);
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_SetDayAndSpawn(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation bedLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_SetOrientation(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_Accelerate(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		buffer.getLong();
		byte directionOrdinal = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_IncrementalRepairBlock(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		buffer.getShort();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_TakeDamangeFromOtherV4(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		int damage = buffer.getInt();
		byte cause = buffer.get();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_MultiBlockPlace(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation target = CodecHelpers.readAbsoluteLocation(buffer);
		FacingDirection orientation = CodecHelpers.readOrientation(buffer);
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_TimeSyncNoop(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		buffer.getLong();
		return new Deprecated_EntityAction<>();
	}

	@SuppressWarnings("unused")
	public static <T extends IMutableMinimalEntity> Deprecated_EntityAction<T> deserialize_TopLevelMovement(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		EntityLocation newVelocity = CodecHelpers.readEntityLocation(buffer);
		byte intensityOrdinal = buffer.get();
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IEntitySubAction<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		return new Deprecated_EntityAction<>();
	}


	@Deprecated
	public Deprecated_EntityAction()
	{
		// These do nothing so we just ignore ivars.
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// This is deprecated so just do nothing (only exists to read old data).
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		// We don't expect this to be called since we can't serialize this type.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// We don't expect this to be called since we can't serialize this type.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// We don't want to save deprecated data - we are just reading it and dropping it.
		return false;
	}

	@Override
	public String toString()
	{
		return "Deprecated IEntityAction";
	}
}
