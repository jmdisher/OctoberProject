package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationEntity;


/**
 * Contains a specific IMutationEntity instance.
 * This is for the case when a client is sending its mutations so there is no ID as the server knows who they are.
 */
public class Packet_MutationEntityFromClient extends Packet
{
	public static final PacketType TYPE = PacketType.MUTATION_ENTITY_FROM_CLIENT;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			IMutationEntity mutation = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_MutationEntityFromClient(mutation);
		};
	}


	public final IMutationEntity mutation;

	public Packet_MutationEntityFromClient(IMutationEntity mutation)
	{
		super(TYPE);
		this.mutation = mutation;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		MutationEntityCodec.serializeToBuffer(buffer, this.mutation);
	}
}
