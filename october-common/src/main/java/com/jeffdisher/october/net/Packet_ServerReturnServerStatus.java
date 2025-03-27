package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * A packet the server sends to a client in response to Packet_ClientPollServerStatus prior to disconnecting.
 */
public class Packet_ServerReturnServerStatus extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.SERVER_RETURN_SERVER_STATUS;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int version = buffer.getInt();
			String serverName = CodecHelpers.readString(buffer);
			int clientCount = buffer.getInt();
			long millis = buffer.getLong();
			return new Packet_ServerReturnServerStatus(version, serverName, clientCount, millis);
		};
	}


	public final int version;
	public final String serverName;
	public final int clientCount;
	public final long millis;

	public Packet_ServerReturnServerStatus(int version, String serverName, int clientCount, long millis)
	{
		super(TYPE);
		this.version = version;
		this.serverName = serverName;
		this.clientCount = clientCount;
		this.millis = millis;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.version);
		CodecHelpers.writeString(buffer, this.serverName);
		buffer.putInt(this.clientCount);
		buffer.putLong(this.millis);
	}
}
