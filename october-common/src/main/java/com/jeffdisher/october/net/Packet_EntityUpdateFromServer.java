package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IEntityUpdate;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains a specific IEntityUpdate instance.
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
			IEntityUpdate update = EntityUpdateCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_EntityUpdateFromServer(entityId, update);
		};
	}


	public final int entityId;
	public final IEntityUpdate update;

	public Packet_EntityUpdateFromServer(int id, IEntityUpdate update)
	{
		super(TYPE);
		this.entityId = id;
		this.update = update;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
		EntityUpdateCodec.serializeToNetworkBuffer(buffer, this.update);
	}
}
