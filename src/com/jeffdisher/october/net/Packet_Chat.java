package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


public class Packet_Chat extends Packet
{
	public static final PacketType TYPE = PacketType.CHAT;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int id = buffer.getInt();
			String message = CodecHelpers.readString(buffer);
			return new Packet_Chat(id, message);
		};
	}


	public final int clientId;
	public final String message;

	public Packet_Chat(int id, String message)
	{
		super(TYPE);
		this.clientId = id;
		this.message = message;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
		CodecHelpers.writeString(buffer, this.message);
	}
}
