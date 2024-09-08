package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sent to all clients when an existing client disconnects.
 */
public class Packet_ClientLeft extends Packet
{
	public static final PacketType TYPE = PacketType.CLIENT_LEFT;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int clientId = buffer.getInt();
			return new Packet_ClientLeft(clientId);
		};
	}


	public final int clientId;

	public Packet_ClientLeft(int clientId)
	{
		super(TYPE);
		this.clientId = clientId;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
	}
}
