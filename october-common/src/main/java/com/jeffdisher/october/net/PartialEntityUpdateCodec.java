package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.mutations.MutationEntitySetPartialEntity;
import com.jeffdisher.october.mutations.PartialEntityUpdateType;
import com.jeffdisher.october.utils.Assert;


/**
 * The codec for serializing and deserializing IPartialEntityUpdate objects inside a
 * Packet_PartialEntityUpdateFromServer packet.
 */
public class PartialEntityUpdateCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IPartialEntityUpdate>[] _CODEC_TABLE = new Function[PartialEntityUpdateType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[MutationEntitySetPartialEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntitySetPartialEntity.deserializeFromNetworkBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IPartialEntityUpdate parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IPartialEntityUpdate parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			PartialEntityUpdateType type = PartialEntityUpdateType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
		}
		return parsed;
	}

	public static void serializeToNetworkBuffer(ByteBuffer buffer, IPartialEntityUpdate entity)
	{
		// Write the type.
		PartialEntityUpdateType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToNetworkBuffer(buffer);
	}
}
