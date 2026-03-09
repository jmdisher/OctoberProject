package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Contains a EntityUpdatePerField instance.
 * Note that this only targets the client's entity, directly, so the entity ID would be redundant.
 */
public class Packet_EntityUpdateFromServer extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.ENTITY_UPDATE_FROM_SERVER;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			EntityUpdatePerField update = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
			return new Packet_EntityUpdateFromServer(update);
		};
	}


	public final EntityUpdatePerField update;

	public Packet_EntityUpdateFromServer(EntityUpdatePerField update)
	{
		super(TYPE);
		this.update = update;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		this.update.serializeToNetworkBuffer(buffer);
	}
}
