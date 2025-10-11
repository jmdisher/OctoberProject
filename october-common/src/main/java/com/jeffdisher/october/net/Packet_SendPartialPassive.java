package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.PartialPassive;


public class Packet_SendPartialPassive extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.PARTIAL_PASSIVE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			PartialPassive entity = CodecHelpers.readPartialPassive(buffer);
			return new Packet_SendPartialPassive(entity);
		};
	}


	public final PartialPassive partial;

	public Packet_SendPartialPassive(PartialPassive partial)
	{
		super(TYPE);
		this.partial = partial;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writePartialPassive(buffer, this.partial);
	}
}
