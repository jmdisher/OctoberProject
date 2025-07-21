package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.EntityChangeAttackEntity;
import com.jeffdisher.october.mutations.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.mutations.IEntitySubAction;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.mutations.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.mutations.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.mutations.EntityChangeSwapArmour;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


public class EntitySubActionCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IEntitySubAction<IMutablePlayerEntity>>[] _CODEC_TABLE = new Function[EntitySubActionType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[EntitySubActionType.UNUSED_MOVE.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeJump.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeJump.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSwim.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSwim.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationPlaceSelectedBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationPlaceSelectedBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntitySelectItem.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntitySelectItem.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityPushItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityPushItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityRequestItemPickUp.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityRequestItemPickUp.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.UNUSED_ITEMS_STORE_TO_INVENTORY.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeIncrementalBlockBreak.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeIncrementalBlockBreak.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraftInBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraftInBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeAttackEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeAttackEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_V2.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_PERIODIC.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeUseSelectedItemOnSelf.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnSelf.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeChangeHotbarSlot.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeChangeHotbarSlot.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSwapArmour.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSwapArmour.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSetBlockLogicState.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSetBlockLogicState.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_CREATIVE.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_OPERATOR_SET_LOCATION.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeSetDayAndSpawn.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSetDayAndSpawn.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.UNUSED_SET_ORIENTATION.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_ACCELERATE.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangeIncrementalBlockRepair.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeIncrementalBlockRepair.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_ENTITY.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntitySubActionType.UNUSED_TAKE_DAMAGE_FROM_OTHER_V4.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		_CODEC_TABLE[EntityChangePlaceMultiBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangePlaceMultiBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntitySubActionType.TESTING_ONLY.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IEntitySubAction<IMutablePlayerEntity> parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IEntitySubAction<IMutablePlayerEntity> parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			EntitySubActionType type = EntitySubActionType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
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
