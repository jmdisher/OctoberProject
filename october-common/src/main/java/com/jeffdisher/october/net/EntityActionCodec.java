package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.Deprecated_EntityAction;
import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.actions.EntityActionOperatorSetCreative;
import com.jeffdisher.october.actions.EntityActionOperatorSetLocation;
import com.jeffdisher.october.actions.EntityActionOperatorSpawnCreature;
import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.actions.EntityActionNudge;
import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


public class EntityActionCodec
{
	@SuppressWarnings("unchecked")
	private static Function<DeserializationContext, IEntityAction<IMutablePlayerEntity>>[] _CODEC_TABLE = new Function[EntityActionType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[EntityActionType.DEPRECATED_MOVE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_Move(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_JUMP.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_Jump(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SWIM.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_Swim(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_BLOCK_PLACE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_BlockPlace(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_CRAFT.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_Craft(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SELECT_ITEM.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_SelectItem(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_ITEMS_REQUEST_PUSH.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_ItemsRequestPush(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_ITEMS_REQUEST_PULL.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_ItemsRequestPull(context);
		_CODEC_TABLE[EntityActionStoreToInventory.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionStoreToInventory.deserialize(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_INCREMENTAL_BREAK_BLOCK.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_IncrementalBreakBlock(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_CRAFT_IN_BLOCK.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_CraftInBlock(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_ATTACK_ENTITY.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_AttackEntity(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_TAKE_DAMAGE_V2.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_TakeDamageV2(context);
		_CODEC_TABLE[EntityActionPeriodic.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionPeriodic.deserialize(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_USE_SELECTED_ITEM_ON_SELF.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_UseSelectedItemOnSelf(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_USE_SELECTED_ITEM_ON_BLOCK.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_UseSelectedItemOnBlock(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_USE_SELECTED_ITEM_ON_ENTITY.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_UseSelectedItemOnEntity(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_CHANGE_HOTBAR_SLOT.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_ChangeHotbarSlot(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SWAP_ARMOUR.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_SwapArmour(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SET_BLOCK_LOGIC_STATE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_SetBlockLogicState(context);
		_CODEC_TABLE[EntityActionOperatorSetCreative.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionOperatorSetCreative.deserialize(context);
		_CODEC_TABLE[EntityActionOperatorSetLocation.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionOperatorSetLocation.deserialize(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SET_DAY_AND_SPAWN.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_SetDayAndSpawn(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_SET_ORIENTATION.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_SetOrientation(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_ACCELERATE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_Accelerate(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_INCREMENTAL_REPAIR_BLOCK.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_IncrementalRepairBlock(context);
		_CODEC_TABLE[EntityActionTakeDamageFromEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionTakeDamageFromEntity.deserialize(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_TAKE_DAMAGE_FROM_OTHER_V4.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_TakeDamangeFromOtherV4(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_MULTI_BLOCK_PLACE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_MultiBlockPlace(context);
		_CODEC_TABLE[EntityActionOperatorSpawnCreature.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionOperatorSpawnCreature.deserialize(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_TIME_SYNC_NOOP.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_TimeSyncNoop(context);
		_CODEC_TABLE[EntityActionType.DEPRECATED_TOP_LEVEL_MOVEMENT.ordinal()] = (DeserializationContext context) -> Deprecated_EntityAction.deserialize_TopLevelMovement(context);
		_CODEC_TABLE[EntityActionSimpleMove.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionSimpleMove.deserialize(context);
		_CODEC_TABLE[EntityActionNudge.TYPE.ordinal()] = (DeserializationContext context) -> EntityActionNudge.deserialize(context);
		_CODEC_TABLE[EntityActionType.TESTING_ONLY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IEntityAction<IMutablePlayerEntity> parseAndSeekContext(DeserializationContext context)
	{
		IEntityAction<IMutablePlayerEntity> parsed = null;
		ByteBuffer buffer = context.buffer();
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			EntityActionType type = EntityActionType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(context);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IEntityAction<IMutablePlayerEntity> entity)
	{
		// Write the type.
		EntityActionType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToBuffer(buffer);
	}
}
