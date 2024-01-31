package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.BreakBlockMutation;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockExtractItems;
import com.jeffdisher.october.mutations.MutationBlockOverwrite;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.utils.Assert;


public class MutationBlockCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IMutationBlock>[] _CODEC_TABLE = new Function[MutationBlockType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[BreakBlockMutation.TYPE.ordinal()] = (ByteBuffer buffer) -> BreakBlockMutation.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockOverwrite.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockOverwrite.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockExtractItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockExtractItems.deserializeFromBuffer(buffer);
		_CODEC_TABLE[MutationBlockStoreItems.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockStoreItems.deserializeFromBuffer(buffer);
		
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
