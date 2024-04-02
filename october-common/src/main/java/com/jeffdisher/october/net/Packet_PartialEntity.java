package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.PartialEntity;


public class Packet_PartialEntity extends Packet
{
	public static final PacketType TYPE = PacketType.PARTIAL_ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			PartialEntity entity = CodecHelpers.readPartialEntity(buffer);
			return new Packet_PartialEntity(entity);
		};
	}


	public final PartialEntity entity;

	public Packet_PartialEntity(PartialEntity entity)
	{
		super(TYPE);
		this.entity = entity;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writePartialEntity(buffer, this.entity);
	}
}
