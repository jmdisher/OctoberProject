package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


public class PacketCodec
{
	@SuppressWarnings("unchecked")
	private static Function<ByteBuffer, Packet>[] _CODEC_TABLE = new Function[PacketType.END_OF_LIST.ordinal()];

	// Our header is 3 bytes (size (2BE), opcode (1)).
	private static final int HEADER_BYTES = Short.BYTES + Byte.BYTES;

	// The size is limited to 40 KiB (for now).
	public static final int MAX_PACKET_BYTES = 40 * 1024;

	// We request that each opcode register itself into the table for decoding, here.
	// We tried doing this as a "push" in static{} for each opcode but they aren't eagerly initialized (not surprising).
	static
	{
		Packet_AssignClientId.register(_CODEC_TABLE);
		Packet_SetClientName.register(_CODEC_TABLE);
		Packet_Chat.register(_CODEC_TABLE);
		
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
			Assert.assertTrue(size <= MAX_PACKET_BYTES);
			if (size <= buffer.limit())
			{
				// Parse the opcode and shift the buffer over.
				byte opcode = buffer.get();
				parsed = _CODEC_TABLE[opcode].apply(buffer);
				// This can't fail.
				Assert.assertTrue(null != parsed);
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
		
		// Write the header.
		int laterPosition = buffer.position();
		int size = laterPosition - initialPosition - HEADER_BYTES;
		buffer.position(initialPosition);
		buffer.putShort((short)size);
		buffer.put((byte) packet.type.ordinal());
		buffer.position(laterPosition);
	}
}
