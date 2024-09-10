package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sent to all clients when a new client connects.
 */
public class Packet_ClientJoined extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.CLIENT_JOINED;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int clientId = buffer.getInt();
			String clientName = CodecHelpers.readString(buffer);
			return new Packet_ClientJoined(clientId, clientName);
		};
	}


	public final int clientId;
	public final String clientName;

	public Packet_ClientJoined(int clientId, String clientName)
	{
		super(TYPE);
		this.clientId = clientId;
		this.clientName = clientName;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
		CodecHelpers.writeString(buffer, this.clientName);
	}
}
