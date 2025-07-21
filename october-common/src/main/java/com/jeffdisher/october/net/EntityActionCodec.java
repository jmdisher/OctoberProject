package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.Deprecated_EntityActionAttackEntity;
import com.jeffdisher.october.mutations.Deprecated_EntityActionBlockPlace;
import com.jeffdisher.october.mutations.Deprecated_EntityActionChangeHotbarSlot;
import com.jeffdisher.october.mutations.Deprecated_EntityActionCraft;
import com.jeffdisher.october.mutations.Deprecated_EntityActionCraftInBlock;
import com.jeffdisher.october.mutations.Deprecated_EntityActionIncrementalBreakBlock;
import com.jeffdisher.october.mutations.Deprecated_EntityActionIncrementalRepairBlock;
import com.jeffdisher.october.mutations.Deprecated_EntityActionItemsRequestPull;
import com.jeffdisher.october.mutations.Deprecated_EntityActionItemsRequestPush;
import com.jeffdisher.october.mutations.Deprecated_EntityActionJump;
import com.jeffdisher.october.mutations.Deprecated_EntityActionMultiBlockPlace;
import com.jeffdisher.october.mutations.Deprecated_EntityActionSelectItem;
import com.jeffdisher.october.mutations.Deprecated_EntityActionSetBlockLogicState;
import com.jeffdisher.october.mutations.Deprecated_EntityActionSetDayAndSpawn;
import com.jeffdisher.october.mutations.Deprecated_EntityActionSwapArmour;
import com.jeffdisher.october.mutations.Deprecated_EntityActionSwim;
import com.jeffdisher.october.mutations.Deprecated_EntityActionUseSelectedItemOnBlock;
import com.jeffdisher.october.mutations.Deprecated_EntityActionUseSelectedItemOnEntity;
import com.jeffdisher.october.mutations.Deprecated_EntityActionUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeAccelerate;
import com.jeffdisher.october.mutations.IEntityAction;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.mutations.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.mutations.EntityChangeOperatorSpawnCreature;
import com.jeffdisher.october.mutations.EntityChangePeriodic;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeSetOrientation;
import com.jeffdisher.october.mutations.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeTimeSync;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.mutations.Deprecated_EntityChangeTakeDamage_V2;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.MutationEntityStoreToInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


public class EntityActionCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IEntityAction<IMutablePlayerEntity>>[] _CODEC_TABLE = new Function[EntityActionType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[Deprecated_EntityChangeMove.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeMove.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionJump.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionJump.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionSwim.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionSwim.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionBlockPlace.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionBlockPlace.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionSelectItem.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionSelectItem.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionItemsRequestPush.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionItemsRequestPush.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionItemsRequestPull.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionItemsRequestPull.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationEntityStoreToInventory.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntityStoreToInventory.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionIncrementalBreakBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionIncrementalBreakBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionCraftInBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionCraftInBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionAttackEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionAttackEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityChangeTakeDamage_V2.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeTakeDamage_V2.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangePeriodic.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangePeriodic.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnSelf.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionUseSelectedItemOnSelf.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionUseSelectedItemOnBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionUseSelectedItemOnEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionChangeHotbarSlot.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionChangeHotbarSlot.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionSwapArmour.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionSwapArmour.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionSetBlockLogicState.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionSetBlockLogicState.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSetCreative.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSetCreative.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSetLocation.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSetLocation.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionSetDayAndSpawn.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionSetDayAndSpawn.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityChangeSetOrientation.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeSetOrientation.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityChangeAccelerate.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeAccelerate.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionIncrementalRepairBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionIncrementalRepairBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTakeDamageFromEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTakeDamageFromEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityChangeTakeDamageFromOther.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeTakeDamageFromOther.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityActionMultiBlockPlace.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityActionMultiBlockPlace.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeOperatorSpawnCreature.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeOperatorSpawnCreature.deserializeFromBuffer(buffer);
		_CODEC_TABLE[Deprecated_EntityChangeTimeSync.TYPE.ordinal()] = (ByteBuffer buffer) -> Deprecated_EntityChangeTimeSync.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityChangeTopLevelMovement.TYPE.ordinal()] = (ByteBuffer buffer) -> EntityChangeTopLevelMovement.deserializeFromBuffer(buffer);
		_CODEC_TABLE[EntityActionType.TESTING_ONLY.ordinal()] = (ByteBuffer buffer) -> { throw Assert.unreachable(); };
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IEntityAction<IMutablePlayerEntity> parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IEntityAction<IMutablePlayerEntity> parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			EntityActionType type = EntityActionType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
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
