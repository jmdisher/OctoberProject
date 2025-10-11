package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.EntityLocation;


public class Packet_SendPartialPassiveUpdate extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.PARTIAL_PASSIVE_UPDATE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int id = buffer.getInt();
			EntityLocation location = CodecHelpers.readEntityLocation(buffer);
			EntityLocation velocity = CodecHelpers.readEntityLocation(buffer);
			return new Packet_SendPartialPassiveUpdate(id, location, velocity);
		};
	}


	public final int entityId;
	public final EntityLocation location;
	public final EntityLocation velocity;

	public Packet_SendPartialPassiveUpdate(int entityId, EntityLocation location, EntityLocation velocity)
	{
		super(TYPE);
		this.entityId = entityId;
		this.location = location;
		this.velocity = velocity;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
		CodecHelpers.writeEntityLocation(buffer, this.location);
		CodecHelpers.writeEntityLocation(buffer, this.velocity);
	}
}
