package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


public class Packet_RemoveEntity extends Packet
{
	public static final PacketType TYPE = PacketType.REMOVE_ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int entityId = buffer.getInt();
			return new Packet_RemoveEntity(entityId);
		};
	}


	public final int entityId;

	public Packet_RemoveEntity(int entityId)
	{
		super(TYPE);
		this.entityId = entityId;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
	}
}
