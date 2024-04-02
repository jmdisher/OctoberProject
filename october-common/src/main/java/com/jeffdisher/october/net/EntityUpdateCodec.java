package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.EntityUpdateType;
import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.mutations.MutationEntitySetEntity;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * The codec for serializing and deserializing IEntityUpdate objects inside a Packet_EntityUpdateFromServer packet.
 */
public class EntityUpdateCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, IEntityUpdate>[] _CODEC_TABLE = new Function[EntityUpdateType.END_OF_LIST.ordinal()];

	// We specifically request that all the mutation types which can be serialized for the network are registered here.
	static
	{
		_CODEC_TABLE[MutationEntitySetEntity.TYPE.ordinal()] = (ByteBuffer buffer) -> MutationEntitySetEntity.deserializeFromNetworkBuffer(buffer);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static IEntityUpdate parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		IEntityUpdate parsed = null;
		// We only use a single byte to describe the type.
		if (buffer.remaining() >= 1)
		{
			byte opcode = buffer.get();
			MutationEntityType type = MutationEntityType.values()[opcode];
			parsed = _CODEC_TABLE[type.ordinal()].apply(buffer);
		}
		return parsed;
	}

	public static void serializeToNetworkBuffer(ByteBuffer buffer, IEntityUpdate entity)
	{
		// Write the type.
		EntityUpdateType type = entity.getType();
		Assert.assertTrue(null != type);
		buffer.put((byte) type.ordinal());
		// Write the rest of the packet.
		entity.serializeToNetworkBuffer(buffer);
	}
}
