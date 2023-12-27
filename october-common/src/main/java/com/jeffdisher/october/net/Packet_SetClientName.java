package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


public class Packet_SetClientName extends Packet
{
	public static final PacketType TYPE = PacketType.SET_CLIENT_NAME;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			String name = CodecHelpers.readString(buffer);
			Assert.assertTrue(null != name);
			return new Packet_SetClientName(name);
		};
	}


	public final String name;

	public Packet_SetClientName(String name)
	{
		super(TYPE);
		this.name = name;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeString(buffer, this.name);
	}
}
