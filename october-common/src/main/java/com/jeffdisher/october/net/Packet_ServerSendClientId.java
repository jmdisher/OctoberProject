package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by the server to complete the handshake (it will disconnect the client if it doesn't like the data).
 */
public class Packet_ServerSendClientId extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.SERVER_SEND_CLIENT_ID;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// We want the client's ID (so we can identify their Entity).
			int id = buffer.getInt();
			Assert.assertTrue(id > 0);
			return new Packet_ServerSendClientId(id);
		};
	}


	public final int clientId;

	public Packet_ServerSendClientId(int id)
	{
		super(TYPE);
		this.clientId = id;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
	}
}
