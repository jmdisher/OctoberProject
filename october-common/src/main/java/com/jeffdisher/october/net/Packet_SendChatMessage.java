package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sent by a client to the server.  The target ID must be non-negative and 0 means "everyone".
 */
public class Packet_SendChatMessage extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.SEND_CHAT_MESSAGE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int targetId = buffer.getInt();
			String message = CodecHelpers.readString(buffer);
			return new Packet_SendChatMessage(targetId, message);
		};
	}


	public final int targetId;
	public final String message;

	public Packet_SendChatMessage(int targetId, String message)
	{
		super(TYPE);
		this.targetId = targetId;
		this.message = message;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.targetId);
		CodecHelpers.writeString(buffer, this.message);
	}
}
