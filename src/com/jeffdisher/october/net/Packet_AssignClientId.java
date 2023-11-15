package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


public class Packet_AssignClientId extends Packet
{
	public static final PacketType TYPE = PacketType.ASSIGN_CLIENT_ID;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// Just read the ID.
			int id = buffer.getInt();
			Assert.assertTrue(id > 0);
			return new Packet_AssignClientId(id);
		};
	}


	public final int clientId;

	public Packet_AssignClientId(int id)
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
