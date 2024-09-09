package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sent by the server to a client.  The source ID is always non-negative and 0 means "from the server console".
 */
public class Packet_ReceiveChatMessage extends Packet
{
	public static final PacketType TYPE = PacketType.RECEIVE_CHAT_MESSAGE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int sourceId = buffer.getInt();
			String message = CodecHelpers.readString(buffer);
			return new Packet_ReceiveChatMessage(sourceId, message);
		};
	}


	public final int sourceId;
	public final String message;

	public Packet_ReceiveChatMessage(int sourceId, String message)
	{
		super(TYPE);
		this.sourceId = sourceId;
		this.message = message;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.sourceId);
		CodecHelpers.writeString(buffer, this.message);
	}
}
