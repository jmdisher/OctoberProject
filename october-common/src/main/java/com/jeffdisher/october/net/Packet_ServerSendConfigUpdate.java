package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by the server to communicate the initial or updated server configuration to the client.
 */
public class Packet_ServerSendConfigUpdate extends Packet
{
	public static final PacketType TYPE = PacketType.SERVER_SEND_CONFIG_UPDATE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int ticksPerDay = buffer.getInt();
			Assert.assertTrue(ticksPerDay > 0);
			return new Packet_ServerSendConfigUpdate(ticksPerDay);
		};
	}


	public final int ticksPerDay;

	public Packet_ServerSendConfigUpdate(int ticksPerDay)
	{
		super(TYPE);
		this.ticksPerDay = ticksPerDay;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.ticksPerDay);
	}
}
