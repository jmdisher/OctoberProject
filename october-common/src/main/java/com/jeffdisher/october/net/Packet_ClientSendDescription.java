package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Sent by a client, immediately upon connecting to a server.
 * It includes the client's supported protocol version, the name, and potentially anything else the client wants to
 * require the server handle.
 */
public class Packet_ClientSendDescription extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.CLIENT_SEND_DESCRIPTION;
	/**
	 * Protocol version 0 was used in v1.0-pre4 and earlier.
	 * Protocol version 1 was used in v1.0-pre6 and earlier.
	 * Protocol version 2 was used in v1.0.1 and earlier.
	 * Protocol version 3 was used in v1.1 and earlier.
	 * Protocol version 4 was used in v1.2.1 and earlier.
	 * Protocol version 5 was used in v1.3 and earlier.
	 * Protocol version 6 was used in v1.4 and earlier.
	 * Protocol version 7 was used in v1.5 and earlier.
	 * Protocol version 8 was used in v1.6 and earlier.
	 * Protocol version 9 was used in v1.7 and earlier.
	 * Protocol version 10 was used in v1.8 and earlier.
	 * Protocol version 11 was used in v1.9 and earlier.
	 * Protocol version 12 was used in v1.10 and earlier.
	 */
	public static final int NETWORK_PROTOCOL_VERSION = 13;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int version = buffer.getInt();
			String name = CodecHelpers.readString(buffer);
			Assert.assertTrue(null != name);
			int cuboidViewDistance = buffer.getInt();
			Assert.assertTrue(cuboidViewDistance >= 0);
			return new Packet_ClientSendDescription(version, name, cuboidViewDistance);
		};
	}


	public final int version;
	public final String name;
	public final int cuboidViewDistance;

	public Packet_ClientSendDescription(int version, String name, int cuboidViewDistance)
	{
		super(TYPE);
		this.version = version;
		this.name = name;
		this.cuboidViewDistance = cuboidViewDistance;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.version);
		CodecHelpers.writeString(buffer, this.name);
		buffer.putInt(this.cuboidViewDistance);
	}
}
