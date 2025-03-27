package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sent by clients, often, when polling a server's status.  The server should disconnect as soon as its response is
 * sent.
 * The server will respond with Packet_ServerReturnServerStatus.
 */
public class Packet_ClientPollServerStatus extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.CLIENT_POLL_SERVER_STATUS;
	/**
	 * Since this type isn't part of the normal dialogue, we will need to check its version independently.
	 */
	public static final int NETWORK_POLL_VERSION = 0;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int version = buffer.getInt();
			long millis = buffer.getLong();
			return new Packet_ClientPollServerStatus(version, millis);
		};
	}


	public final int version;
	public final long millis;
	public Packet_ClientPollServerStatus(int version, long millis)
	{
		super(TYPE);
		this.version = version;
		this.millis = millis;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.version);
		buffer.putLong(this.millis);
	}
}
