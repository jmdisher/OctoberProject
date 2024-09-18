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
	public static final int NETWORK_PROTOCOL_VERSION = 0;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int version = buffer.getInt();
			String name = CodecHelpers.readString(buffer);
			Assert.assertTrue(null != name);
			return new Packet_ClientSendDescription(version, name);
		};
	}


	public final int version;
	public final String name;

	public Packet_ClientSendDescription(int version, String name)
	{
		super(TYPE);
		this.version = version;
		this.name = name;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.version);
		CodecHelpers.writeString(buffer, this.name);
	}
}
