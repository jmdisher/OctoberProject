package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by the server to communicate the initial or updated server configuration to the client.
 */
public class Packet_ServerSendConfigUpdate extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.SERVER_SEND_CONFIG_UPDATE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int ticksPerDay = buffer.getInt();
			Assert.assertTrue(ticksPerDay > 0);
			int dayStartTick = buffer.getInt();
			Assert.assertTrue(dayStartTick >= 0);
			return new Packet_ServerSendConfigUpdate(ticksPerDay, dayStartTick);
		};
	}


	public final int ticksPerDay;
	public final int dayStartTick;

	public Packet_ServerSendConfigUpdate(int ticksPerDay, int dayStartTick)
	{
		super(TYPE);
		this.ticksPerDay = ticksPerDay;
		this.dayStartTick = dayStartTick;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.ticksPerDay);
		buffer.putInt(this.dayStartTick);
	}
}
