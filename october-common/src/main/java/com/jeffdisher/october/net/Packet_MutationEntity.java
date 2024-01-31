package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains a specific IMutationEntity instance.
 * Note that the entity ID is ignored when going client->server but is valid when going from server->client.
 */
public class Packet_MutationEntity extends Packet
{
	public static final PacketType TYPE = PacketType.MUTATION_ENTITY;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int entityId = buffer.getInt();
			Assert.assertTrue(entityId > 0);
			IMutationEntity mutation = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_MutationEntity(entityId, mutation);
		};
	}


	public final int entityId;
	public final IMutationEntity mutation;

	public Packet_MutationEntity(int id, IMutationEntity mutation)
	{
		super(TYPE);
		this.entityId = id;
		this.mutation = mutation;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(this.entityId);
		MutationEntityCodec.serializeToBuffer(buffer, this.mutation);
	}
}
