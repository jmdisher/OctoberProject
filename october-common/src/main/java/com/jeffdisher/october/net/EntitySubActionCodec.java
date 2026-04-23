package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.subactions.EntityChangeAttackEntity;
import com.jeffdisher.october.subactions.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.subactions.EntityChangeCraft;
import com.jeffdisher.october.subactions.EntityChangeCraftInBlock;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.subactions.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.subactions.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.subactions.EntityChangeSwapArmour;
import com.jeffdisher.october.subactions.EntityChangeSwim;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.subactions.EntitySubActionChargeWeapon;
import com.jeffdisher.october.subactions.EntitySubActionDropItemsAsPassive;
import com.jeffdisher.october.subactions.EntitySubActionLadderAscend;
import com.jeffdisher.october.subactions.EntitySubActionLadderDescend;
import com.jeffdisher.october.subactions.EntitySubActionPickUpPassive;
import com.jeffdisher.october.subactions.EntitySubActionPopOutOfBlock;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.subactions.EntitySubActionRequestSwapSpecialSlot;
import com.jeffdisher.october.subactions.EntitySubActionTravelViaBlock;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.subactions.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.subactions.MutationEntitySelectItem;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


public class EntitySubActionCodec
{
	@SuppressWarnings("unchecked")
	private static Function<DeserializationContext, IEntitySubAction<IMutablePlayerEntity>>[] _CODEC_TABLE = new Function[EntitySubActionType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[EntitySubActionType.UNUSED_MOVE.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeJump.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeJump.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeSwim.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeSwim.deserializeFromContext(context);
		_CODEC_TABLE[MutationPlaceSelectedBlock.TYPE.ordinal()] = (DeserializationContext context) -> MutationPlaceSelectedBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeCraft.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeCraft.deserializeFromContext(context);
		_CODEC_TABLE[MutationEntitySelectItem.TYPE.ordinal()] = (DeserializationContext context) -> MutationEntitySelectItem.deserializeFromContext(context);
		_CODEC_TABLE[MutationEntityPushItems.TYPE.ordinal()] = (DeserializationContext context) -> MutationEntityPushItems.deserializeFromContext(context);
		_CODEC_TABLE[MutationEntityRequestItemPickUp.TYPE.ordinal()] = (DeserializationContext context) -> MutationEntityRequestItemPickUp.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_ITEMS_STORE_TO_INVENTORY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeIncrementalBlockBreak.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeIncrementalBlockBreak.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeCraftInBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeCraftInBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeAttackEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeAttackEntity.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_V2.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_PERIODIC.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeUseSelectedItemOnSelf.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeUseSelectedItemOnSelf.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeUseSelectedItemOnBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeUseSelectedItemOnEntity.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeChangeHotbarSlot.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeChangeHotbarSlot.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeSwapArmour.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeSwapArmour.deserializeFromContext(context);
		_CODEC_TABLE[EntityChangeSetBlockLogicState.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeSetBlockLogicState.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_CREATIVE.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_LOCATION.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeSetDayAndSpawn.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeSetDayAndSpawn.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_SET_ORIENTATION.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_ACCELERATE.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeIncrementalBlockRepair.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeIncrementalBlockRepair.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_ENTITY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_OTHER_V4.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangePlaceMultiBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangePlaceMultiBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionLadderAscend.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionLadderAscend.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionLadderDescend.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionLadderDescend.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionRequestSwapSpecialSlot.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionRequestSwapSpecialSlot.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionTravelViaBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionTravelViaBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionDropItemsAsPassive.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionDropItemsAsPassive.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPickUpPassive.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPickUpPassive.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPopOutOfBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPopOutOfBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionChargeWeapon.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionChargeWeapon.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionReleaseWeapon.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionReleaseWeapon.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.TESTING_ONLY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IEntitySubAction<IMutablePlayerEntity> parseAndSeekFlippedBuffer(DeserializationContext context)
	{
		IEntitySubAction<IMutablePlayerEntity> parsed = null;
		ByteBuffer buffer = context.buffer();
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			EntitySubActionType type = EntitySubActionType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(context);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IEntitySubAction<IMutablePlayerEntity> entity)
	{
		// Write the type.
		EntitySubActionType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToBuffer(buffer);
	}
}
