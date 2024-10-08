package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IPartialEntityUpdate;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains a specific IPartialEntityUpdate instance.
 * This is coming from the server so it includes the associated entity ID.
 */
public class Packet_PartialEntityUpdateFromServer extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.PARTIAL_ENTITY_UPDATE_FROM_SERVER;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int entityId = buffer.getInt();
			// Positive entities are players, negative are server-generated, but 0 is invalid.
			Assert.assertTrue(0 != entityId);
			IPartialEntityUpdate update = PartialEntityUpdateCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_PartialEntityUpdateFromServer(entityId, update);
		};
	}


	public final int entityId;
	public final IPartialEntityUpdate update;

	public Packet_PartialEntityUpdateFromServer(int id, IPartialEntityUpdate update)
	{
		super(TYPE);
		this.entityId = id;
		this.update = update;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
		PartialEntityUpdateCodec.serializeToNetworkBuffer(buffer, this.update);
	}
}
