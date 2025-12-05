package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.utils.Assert;


/**
 * Contains a MutationEntitySetEntity instance.
 * This is coming from the server so it includes the associated entity ID.
 */
public class Packet_EntityUpdateFromServer extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.ENTITY_UPDATE_FROM_SERVER;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int entityId = buffer.getInt();
			Assert.assertTrue(entityId > 0);
			EntityUpdatePerField update = EntityUpdatePerField.deserializeFromNetworkBuffer(buffer);
			return new Packet_EntityUpdateFromServer(entityId, update);
		};
	}


	public final int entityId;
	public final EntityUpdatePerField update;

	public Packet_EntityUpdateFromServer(int id, EntityUpdatePerField update)
	{
		super(TYPE);
		this.entityId = id;
		this.update = update;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
		this.update.serializeToNetworkBuffer(buffer);
	}
}
