package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeAttackEntity;
import com.jeffdisher.october.mutations.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeCraftInBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.mutations.EntityChangeOperatorSpawnCreature;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.mutations.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.mutations.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.mutations.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.mutations.EntityChangeSetOrientation;
import com.jeffdisher.october.mutations.EntityChangeSwapArmour;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.EntityChangeTakeDamage_V2;
import com.jeffdisher.october.mutations.EntityChangeTimeSync;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


public class MutationEntityCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IMutationEntity<IMutablePlayerEntity>>[] _CODEC_TABLE = new Function[MutationEntityType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[EntityChangeMove.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeMove.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeJump.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeJump.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSwim.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSwim.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationPlaceSelectedBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationPlaceSelectedBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntitySelectItem.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntitySelectItem.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityPushItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityPushItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityRequestItemPickUp.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityRequestItemPickUp.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityStoreToInventory.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityStoreToInventory.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeIncrementalBlockBreak.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeIncrementalBlockBreak.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeCraftInBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeCraftInBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeAttackEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeAttackEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTakeDamage_V2.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTakeDamage_V2.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangePeriodic.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangePeriodic.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnSelf.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnSelf.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeUseSelectedItemOnEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeUseSelectedItemOnEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeChangeHotbarSlot.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeChangeHotbarSlot.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSwapArmour.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSwapArmour.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSetBlockLogicState.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSetBlockLogicState.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSetCreative.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSetCreative.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSetLocation.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSetLocation.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSetDayAndSpawn.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSetDayAndSpawn.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeSetOrientation.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeSetOrientation.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeAccelerate.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeAccelerate.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeIncrementalBlockRepair.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeIncrementalBlockRepair.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTakeDamageFromEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTakeDamageFromEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTakeDamageFromOther.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTakeDamageFromOther.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangePlaceMultiBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangePlaceMultiBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSpawnCreature.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSpawnCreature.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTimeSync.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTimeSync.deserializeFromBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IMutationEntity<IMutablePlayerEntity> parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IMutationEntity<IMutablePlayerEntity> parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			MutationEntityType type = MutationEntityType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IMutationEntity<IMutablePlayerEntity> entity)
	{
		// Write the type.
		MutationEntityType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToBuffer(buffer);
	}
}
