package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IMutationBlock;


/**
 * Contains a specific IMutationBlock instance.
 * Note that these only travel from server to client.
 */
public class Packet_MutationBlock extends Packet
{
	public static final PacketType TYPE = PacketType.MUTATION_BLOCK;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			IMutationBlock mutation = MutationBlockCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_MutationBlock(mutation);
		};
	}


	public final IMutationBlock mutation;

	public Packet_MutationBlock(IMutationBlock mutation)
	{
		super(TYPE);
		this.mutation = mutation;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		MutationBlockCodec.serializeToBuffer(buffer, this.mutation);
	}
}
