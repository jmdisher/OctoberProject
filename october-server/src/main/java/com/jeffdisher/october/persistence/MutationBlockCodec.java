package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockCraft;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockForceGrow;
import com.jeffdisher.october.mutations.MutationBlockFurnaceCraft;
import com.jeffdisher.october.mutations.MutationBlockPeriodic;
import com.jeffdisher.october.mutations.MutationBlockIncrementalBreak;
import com.jeffdisher.october.mutations.MutationBlockIncrementalRepair;
import com.jeffdisher.october.mutations.MutationBlockLiquidFlowInto;
import com.jeffdisher.october.mutations.MutationBlockLogicChange;
import com.jeffdisher.october.mutations.MutationBlockOverwriteByEntity;
import com.jeffdisher.october.mutations.MutationBlockOverwriteInternal;
import com.jeffdisher.october.mutations.MutationBlockPushToBlock;
import com.jeffdisher.october.mutations.MutationBlockReplace;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.mutations.MutationBlockUpdate;
import com.jeffdisher.october.utils.Assert;


/**
 * NOTE:  IMutationBlock instances are never sent over the network but may be written to disk.
 */
public class MutationBlockCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IMutationBlock>[] _CODEC_TABLE = new Function[MutationBlockType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[MutationBlockOverwriteInternal.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockOverwriteInternal.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockExtractItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockExtractItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockStoreItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockStoreItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockIncrementalBreak.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockIncrementalBreak.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockFurnaceCraft.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockFurnaceCraft.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockUpdate.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockUpdate.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockForceGrow.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockForceGrow.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockReplace.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockReplace.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockPushToBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockPushToBlock.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockLogicChange.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockLogicChange.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockPeriodic.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockPeriodic.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockIncrementalRepair.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockIncrementalRepair.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockOverwriteByEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockOverwriteByEntity.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockLiquidFlowInto.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockLiquidFlowInto.deserializeFromBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IMutationBlock parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IMutationBlock parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			MutationBlockType type = MutationBlockType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
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
