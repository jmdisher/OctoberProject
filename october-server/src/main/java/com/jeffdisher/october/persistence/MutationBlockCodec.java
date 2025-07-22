package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockBurnDown;
import com.jeffdisher.october.mutations.MutationBlockCraft;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockForceGrow;
import com.jeffdisher.october.mutations.MutationBlockFurnaceCraft;
import com.jeffdisher.october.mutations.MutationBlockGrowGroundCover;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockPhase2Multi;
import com.jeffdisher.october.mutations.MutationBlockPlaceMultiBlock;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockIncrementalRepair;
import com.jeffdisher.october.mutations.MutationBlockInternalSetLogicState;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.mutations.MutationBlockLogicChange;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity_V5;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.mutations.MutationBlockPushToBlock;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationBlockSetLogicState;
import com.jeffdisher.october.mutations.MutationBlockStartFire;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.utils.Assert;


/**
 * NOTE:  IMutationBlock instances are never sent over the network but may be written to disk.
 */
public class MutationBlockCodec
{
	@SuppressWarnings("unchecked")
	private static Function<DeserializationContext, IMutationBlock>[] _CODEC_TABLE = new Function[MutationBlockType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[MutationBlockOverwriteInternal.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockOverwriteInternal.deserialize(context);
		_CODEC_TABLE[MutationBlockExtractItems.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockExtractItems.deserialize(context);
		_CODEC_TABLE[MutationBlockStoreItems.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockStoreItems.deserialize(context);
		_CODEC_TABLE[MutationBlockIncrementalBreak.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockIncrementalBreak.deserialize(context);
		_CODEC_TABLE[MutationBlockCraft.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockCraft.deserialize(context);
		_CODEC_TABLE[MutationBlockFurnaceCraft.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockFurnaceCraft.deserialize(context);
		_CODEC_TABLE[MutationBlockUpdate.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockUpdate.deserialize(context);
		_CODEC_TABLE[MutationBlockForceGrow.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockForceGrow.deserialize(context);
		_CODEC_TABLE[MutationBlockReplace.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockReplace.deserialize(context);
		_CODEC_TABLE[MutationBlockPushToBlock.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockPushToBlock.deserialize(context);
		_CODEC_TABLE[MutationBlockLogicChange.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockLogicChange.deserialize(context);
		_CODEC_TABLE[MutationBlockPeriodic.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockPeriodic.deserialize(context);
		_CODEC_TABLE[MutationBlockIncrementalRepair.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockIncrementalRepair.deserialize(context);
		_CODEC_TABLE[MutationBlockOverwriteByEntity_V5.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockOverwriteByEntity_V5.deserialize(context);
		_CODEC_TABLE[MutationBlockLiquidFlowInto.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockLiquidFlowInto.deserialize(context);
		_CODEC_TABLE[MutationBlockStartFire.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockStartFire.deserialize(context);
		_CODEC_TABLE[MutationBlockBurnDown.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockBurnDown.deserialize(context);
		_CODEC_TABLE[MutationBlockSetLogicState.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockSetLogicState.deserialize(context);
		_CODEC_TABLE[MutationBlockPlaceMultiBlock.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockPlaceMultiBlock.deserialize(context);
		_CODEC_TABLE[MutationBlockPhase2Multi.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockPhase2Multi.deserialize(context);
		_CODEC_TABLE[MutationBlockGrowGroundCover.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockGrowGroundCover.deserialize(context);
		_CODEC_TABLE[MutationBlockInternalSetLogicState.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockInternalSetLogicState.deserialize(context);
		_CODEC_TABLE[MutationBlockOverwriteByEntity.TYPE.ordinal()] = (DeserializationContext context) -> MutationBlockOverwriteByEntity.deserialize(context);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IMutationBlock parseAndSeekContext(DeserializationContext context)
	{
		IMutationBlock parsed = null;
		ByteBuffer buffer = context.buffer();
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			MutationBlockType type = MutationBlockType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(context);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IMutationBlock mutation)
	{
		// Write the type.
		MutationBlockType type = mutation.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		mutation.serializeToBuffer(buffer);
	}
}
