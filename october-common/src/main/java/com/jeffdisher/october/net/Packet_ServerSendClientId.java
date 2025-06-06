package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.utils.Assert;


/**
 * Sent by the server to complete the handshake (it will disconnect the client if it doesn't like the data).
 * This only sends information the client needs to interpret the data (like their own ID) and other immutable values
 * they may need from the server (like the tick rate).
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
			// We want the server's millis/tick (so we can properly fake future ticks).
			long millisPerTick = buffer.getLong();
			// Since the default view distance is 1, the maximum must be at least that.
			int viewDistanceMaximum = buffer.getInt();
			Assert.assertTrue(viewDistanceMaximum >= MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
			return new Packet_ServerSendClientId(id, millisPerTick, viewDistanceMaximum);
		};
	}


	public final int clientId;
	public final long millisPerTick;
	public final int viewDistanceMaximum;

	public Packet_ServerSendClientId(int id, long millisPerTick, int viewDistanceMaximum)
	{
		super(TYPE);
		this.clientId = id;
		this.millisPerTick = millisPerTick;
		this.viewDistanceMaximum = viewDistanceMaximum;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.clientId);
		buffer.putLong(this.millisPerTick);
		buffer.putInt(this.viewDistanceMaximum);
	}
}
