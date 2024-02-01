package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains a specific IMutationEntity instance.
 * This is coming from the server so it includes the associated entity ID.
 */
public class Packet_MutationEntityFromServer extends Packet
{
	public static final PacketType TYPE = PacketType.MUTATION_ENTITY_FROM_SERVER;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			int entityId = buffer.getInt();
			Assert.assertTrue(entityId > 0);
			IMutationEntity mutation = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_MutationEntityFromServer(entityId, mutation);
		};
	}


	public final int entityId;
	public final IMutationEntity mutation;

	public Packet_MutationEntityFromServer(int id, IMutationEntity mutation)
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
