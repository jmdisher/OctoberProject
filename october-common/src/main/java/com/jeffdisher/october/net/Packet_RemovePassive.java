package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


public class Packet_RemovePassive extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.REMOVE_PASSIVE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int id = buffer.getInt();
			return new Packet_RemovePassive(id);
		};
	}


	public final int entityId;

	public Packet_RemovePassive(int entityId)
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
