package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.subactions.EntitySubActionAttackEntity;
import com.jeffdisher.october.subactions.EntitySubActionChangeHotbarSlot;
import com.jeffdisher.october.subactions.EntitySubActionCraft;
import com.jeffdisher.october.subactions.EntitySubActionCraftInBlock;
import com.jeffdisher.october.subactions.EntitySubActionIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntitySubActionIncrementalBlockRepair;
import com.jeffdisher.october.subactions.EntitySubActionJump;
import com.jeffdisher.october.subactions.EntitySubActionPlaceMultiBlock;
import com.jeffdisher.october.subactions.EntitySubActionSetBlockLogicState;
import com.jeffdisher.october.subactions.EntitySubActionSetDayAndSpawn;
import com.jeffdisher.october.subactions.EntitySubActionSwapArmour;
import com.jeffdisher.october.subactions.EntitySubActionSwim;
import com.jeffdisher.october.subactions.EntitySubActionUseSelectedItemOnBlock;
import com.jeffdisher.october.subactions.EntitySubActionUseSelectedItemOnEntity;
import com.jeffdisher.october.subactions.EntitySubActionUseSelectedItemOnSelf;
import com.jeffdisher.october.subactions.EntitySubActionChargeWeapon;
import com.jeffdisher.october.subactions.EntitySubActionDropItemsAsPassive;
import com.jeffdisher.october.subactions.EntitySubActionLadderAscend;
import com.jeffdisher.october.subactions.EntitySubActionLadderDescend;
import com.jeffdisher.october.subactions.EntitySubActionPickUpPassive;
import com.jeffdisher.october.subactions.EntitySubActionPopOutOfBlock;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.subactions.EntitySubActionRequestSwapSpecialSlot;
import com.jeffdisher.october.subactions.EntitySubActionSendTrade;
import com.jeffdisher.october.subactions.EntitySubActionStepUp;
import com.jeffdisher.october.subactions.EntitySubActionTravelViaBlock;
import com.jeffdisher.october.subactions.EntitySubActionType;
import com.jeffdisher.october.subactions.EntitySubActionPushItems;
import com.jeffdisher.october.subactions.EntitySubActionRequestItemPickUp;
import com.jeffdisher.october.subactions.EntitySubActionSelectItem;
import com.jeffdisher.october.subactions.EntitySubActionPlaceSelectedBlock;
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
		_CODEC_TABLE[EntitySubActionJump.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionJump.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionSwim.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSwim.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPlaceSelectedBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPlaceSelectedBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionCraft.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionCraft.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionSelectItem.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSelectItem.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPushItems.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPushItems.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionRequestItemPickUp.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionRequestItemPickUp.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_ITEMS_STORE_TO_INVENTORY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionIncrementalBlockBreak.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionIncrementalBlockBreak.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionCraftInBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionCraftInBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionAttackEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionAttackEntity.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_V2.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_PERIODIC.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionUseSelectedItemOnSelf.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionUseSelectedItemOnSelf.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionUseSelectedItemOnBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionUseSelectedItemOnBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionUseSelectedItemOnEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionUseSelectedItemOnEntity.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionChangeHotbarSlot.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionChangeHotbarSlot.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionSwapArmour.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSwapArmour.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionSetBlockLogicState.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSetBlockLogicState.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_CREATIVE.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_LOCATION.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionSetDayAndSpawn.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSetDayAndSpawn.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_SET_ORIENTATION.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_ACCELERATE.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionIncrementalBlockRepair.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionIncrementalBlockRepair.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_ENTITY.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_OTHER_V4.ordinal()] = (DeserializationContext context) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionPlaceMultiBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPlaceMultiBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionLadderAscend.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionLadderAscend.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionLadderDescend.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionLadderDescend.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionRequestSwapSpecialSlot.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionRequestSwapSpecialSlot.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionTravelViaBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionTravelViaBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionDropItemsAsPassive.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionDropItemsAsPassive.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPickUpPassive.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPickUpPassive.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionPopOutOfBlock.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionPopOutOfBlock.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionChargeWeapon.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionChargeWeapon.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionReleaseWeapon.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionReleaseWeapon.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionStepUp.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionStepUp.deserializeFromContext(context);
		_CODEC_TABLE[EntitySubActionSendTrade.TYPE.ordinal()] = (DeserializationContext context) -> EntitySubActionSendTrade.deserializeFromContext(context);
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
