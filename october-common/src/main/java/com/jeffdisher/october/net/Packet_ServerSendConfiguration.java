package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by the server to complete the handshake (it will disconnect the client if it doesn't like the data).
 */
public class Packet_ServerSendConfiguration extends Packet
{
	public static final PacketType TYPE = PacketType.SERVER_SEND_CONFIGURATION;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// We want the client's ID (so we can identify their Entity).
			int id = buffer.getInt();
			Assert.assertTrue(id > 0);
			// Also, see what the tick rate is.
			long millisPerTick = buffer.getLong();
			Assert.assertTrue(millisPerTick > 0L);
			return new Packet_ServerSendConfiguration(id, millisPerTick);
		};
	}


	public final int clientId;
	public final long millisPerTick;

	public Packet_ServerSendConfiguration(int id, long millisPerTick)
	{
		super(TYPE);
		this.clientId = id;
		this.millisPerTick = millisPerTick;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
		buffer.putLong(this.millisPerTick);
	}
}
