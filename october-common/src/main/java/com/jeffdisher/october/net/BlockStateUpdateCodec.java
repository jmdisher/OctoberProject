package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.BlockStateUpdateType;
import com.jeffdisher.october.mutations.IBlockStateUpdate;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.utils.Assert;


public class BlockStateUpdateCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IBlockStateUpdate>[] _CODEC_TABLE = new Function[BlockStateUpdateType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[MutationBlockSetBlock.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationBlockSetBlock.deserializeFromBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IBlockStateUpdate parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IBlockStateUpdate parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			BlockStateUpdateType type = BlockStateUpdateType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, IBlockStateUpdate mutation)
	{
		// Write the type.
		BlockStateUpdateType type = mutation.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		mutation.serializeToBuffer(buffer);
	}
}
