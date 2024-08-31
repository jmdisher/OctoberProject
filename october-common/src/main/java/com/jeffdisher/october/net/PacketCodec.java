package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


public class PacketCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, Packet>[] _CODEC_TABLE = new Function[PacketType.END_OF_LIST.ordinal()];

	// Our header is 3 bytes (size (2BE), opcode (1)).
	public static final int HEADER_BYTES = Short.BYTES + Byte.BYTES;

	// The maximum size of a single packet is 64 KiB, including the header.
	public static final int MAX_PACKET_BYTES = 64 * 1024;

	// We request that each opcode register itself into the table for decoding, here.
	// We tried doing this as a "push" in static{} for each opcode but they aren't eagerly initialized (not surprising).
	static
	{
		Packet_ClientSendDescription.register(_CODEC_TABLE);
		Packet_ServerSendClientId.register(_CODEC_TABLE);
		Packet_Chat.register(_CODEC_TABLE);
		Packet_CuboidStart.register(_CODEC_TABLE);
		Packet_CuboidFragment.register(_CODEC_TABLE);
		Packet_Entity.register(_CODEC_TABLE);
		Packet_PartialEntity.register(_CODEC_TABLE);
		Packet_MutationEntityFromClient.register(_CODEC_TABLE);
		Packet_EntityUpdateFromServer.register(_CODEC_TABLE);
		Packet_PartialEntityUpdateFromServer.register(_CODEC_TABLE);
		Packet_EndOfTick.register(_CODEC_TABLE);
		Packet_RemoveEntity.register(_CODEC_TABLE);
		Packet_RemoveCuboid.register(_CODEC_TABLE);
		Packet_BlockStateUpdate.register(_CODEC_TABLE);
		Packet_ServerSendConfigUpdate.register(_CODEC_TABLE);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _CODEC_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _CODEC_TABLE[i]);
		}
	}


	public static Packet parseAndSeekFlippedBuffer(ByteBuffer buffer)
	{
		Packet parsed = null;
		if (buffer.remaining() >= HEADER_BYTES)
		{
			// We will try to process the data, reseting the position on failure.
			int start = buffer.position();
			int size = Short.toUnsignedInt(buffer.getShort());
			// Note that the buffer size CANNOT be less than the header size so, if it is 0, this must be full and overflowed.
			if (0 == size)
			{
				size = 0x10000;
			}
			Assert.assertTrue(size <= MAX_PACKET_BYTES);
			Assert.assertTrue(size >= HEADER_BYTES);
			int limit = buffer.limit();
			int end = start + size;
			if (end <= limit)
			{
				// Update the limit so the parser knows how much it should be seeing.
				// (the size includes the header).
				buffer.limit(end);
				// Parse the opcode and shift the buffer over.
				byte opcode = buffer.get();
				parsed = _CODEC_TABLE[opcode].apply(buffer);
				// This can't fail.
				Assert.assertTrue(null != parsed);
				// Reset the limit.
				buffer.limit(limit);
			}
			else
			{
				// We couldn't read this yet so reset the position.
				buffer.position(start);
			}
		}
		return parsed;
	}

	public static void serializeToBuffer(ByteBuffer buffer, Packet packet)
	{
		int initialPosition = buffer.position();
		// Skip ahead to add the header later.
		buffer.position(initialPosition + HEADER_BYTES);
		packet.serializeToBuffer(buffer);
		
		// Write the header - NOTE that the header is included in the size.
		int laterPosition = buffer.position();
		int size = laterPosition - initialPosition;
		buffer.position(initialPosition);
		buffer.putShort((short)size);
		buffer.put((byte) packet.type.ordinal());
		buffer.position(laterPosition);
	}
}
