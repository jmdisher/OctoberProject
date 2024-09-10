package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.Entity;


public class Packet_Entity extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			Entity entity = CodecHelpers.readEntity(buffer);
			return new Packet_Entity(entity);
		};
	}


	public final Entity entity;

	public Packet_Entity(Entity entity)
	{
		super(TYPE);
		this.entity = entity;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntity(buffer, this.entity);
	}
}
