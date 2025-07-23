package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.Deprecated_EntityActionAttackEntity;
import com.jeffdisher.october.actions.Deprecated_EntityActionBlockPlace;
import com.jeffdisher.october.actions.Deprecated_EntityActionChangeHotbarSlot;
import com.jeffdisher.october.actions.Deprecated_EntityActionCraft;
import com.jeffdisher.october.actions.Deprecated_EntityActionCraftInBlock;
import com.jeffdisher.october.actions.Deprecated_EntityActionIncrementalBreakBlock;
import com.jeffdisher.october.actions.Deprecated_EntityActionIncrementalRepairBlock;
import com.jeffdisher.october.actions.Deprecated_EntityActionItemsRequestPull;
import com.jeffdisher.october.actions.Deprecated_EntityActionItemsRequestPush;
import com.jeffdisher.october.actions.Deprecated_EntityActionJump;
import com.jeffdisher.october.actions.Deprecated_EntityActionMultiBlockPlace;
import com.jeffdisher.october.actions.Deprecated_EntityActionSelectItem;
import com.jeffdisher.october.actions.Deprecated_EntityActionSetBlockLogicState;
import com.jeffdisher.october.actions.Deprecated_EntityActionSetDayAndSpawn;
import com.jeffdisher.october.actions.Deprecated_EntityActionSwapArmour;
import com.jeffdisher.october.actions.Deprecated_EntityActionSwim;
import com.jeffdisher.october.actions.Deprecated_EntityActionUseSelectedItemOnBlock;
import com.jeffdisher.october.actions.Deprecated_EntityActionUseSelectedItemOnEntity;
import com.jeffdisher.october.actions.Deprecated_EntityActionUseSelectedItemOnSelf;
import com.jeffdisher.october.actions.Deprecated_EntityChangeAccelerate;
import com.jeffdisher.october.actions.Deprecated_EntityChangeMove;
import com.jeffdisher.october.actions.Deprecated_EntityChangeSetOrientation;
import com.jeffdisher.october.actions.Deprecated_EntityChangeTakeDamageFromOther;
import com.jeffdisher.october.actions.Deprecated_EntityChangeTakeDamage_V2;
import com.jeffdisher.october.actions.Deprecated_EntityChangeTimeSync;
import com.jeffdisher.october.actions.EntityChangeOperatorSetCreative;
import com.jeffdisher.october.actions.EntityChangeOperatorSetLocation;
import com.jeffdisher.october.actions.EntityChangeOperatorSpawnCreature;
import com.jeffdisher.october.actions.EntityChangePeriodic;
import com.jeffdisher.october.actions.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
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
		_CODEC_TABLE[Deprecated_EntityChangeMove.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeMove.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionJump.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionJump.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionSwim.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionSwim.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionBlockPlace.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionBlockPlace.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionCraft.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionCraft.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionSelectItem.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionSelectItem.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionItemsRequestPush.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionItemsRequestPush.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionItemsRequestPull.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionItemsRequestPull.deserialize(context);
		_CODEC_TABLE[MutationEntityStoreToInventory.TYPE.ordinal()] = (DeserializationContext context) -> MutationEntityStoreToInventory.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionIncrementalBreakBlock.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionIncrementalBreakBlock.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionCraftInBlock.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionCraftInBlock.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionAttackEntity.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionAttackEntity.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityChangeTakeDamage_V2.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeTakeDamage_V2.deserialize(context);
		_CODEC_TABLE[EntityChangePeriodic.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangePeriodic.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnSelf.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionUseSelectedItemOnSelf.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnBlock.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionUseSelectedItemOnBlock.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionUseSelectedItemOnEntity.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionUseSelectedItemOnEntity.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionChangeHotbarSlot.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionChangeHotbarSlot.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionSwapArmour.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionSwapArmour.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionSetBlockLogicState.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionSetBlockLogicState.deserialize(context);
		_CODEC_TABLE[EntityChangeOperatorSetCreative.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeOperatorSetCreative.deserialize(context);
		_CODEC_TABLE[EntityChangeOperatorSetLocation.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeOperatorSetLocation.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionSetDayAndSpawn.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionSetDayAndSpawn.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityChangeSetOrientation.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeSetOrientation.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityChangeAccelerate.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeAccelerate.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionIncrementalRepairBlock.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionIncrementalRepairBlock.deserialize(context);
		_CODEC_TABLE[EntityChangeTakeDamageFromEntity.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeTakeDamageFromEntity.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityChangeTakeDamageFromOther.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeTakeDamageFromOther.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityActionMultiBlockPlace.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityActionMultiBlockPlace.deserialize(context);
		_CODEC_TABLE[EntityChangeOperatorSpawnCreature.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeOperatorSpawnCreature.deserialize(context);
		_CODEC_TABLE[Deprecated_EntityChangeTimeSync.TYPE.ordinal()] = (DeserializationContext context) -> Deprecated_EntityChangeTimeSync.deserialize(context);
		_CODEC_TABLE[EntityChangeTopLevelMovement.TYPE.ordinal()] = (DeserializationContext context) -> EntityChangeTopLevelMovement.deserialize(context);
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
