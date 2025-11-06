package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.Entity;


public class Packet_Entity extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// This is network so we don't need version-specific decoding.
			DeserializationContext context = new DeserializationContext(Environment.getShared()
				, buffer
				, 0L
				, false
			);
			Entity entity = CodecHelpers.readEntityNetwork(context);
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
		CodecHelpers.writeEntityNetwork(buffer, this.entity);
	}
}
