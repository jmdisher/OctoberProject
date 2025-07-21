package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.types.IMutablePlayerEntity;


/**
 * Contains a top-level movement change from a client.
 */
public class Packet_MutationEntityFromClient extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.MUTATION_ENTITY_FROM_CLIENT;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			EntityChangeTopLevelMovement<IMutablePlayerEntity> mutation = (EntityChangeTopLevelMovement<IMutablePlayerEntity>) EntityActionCodec.parseAndSeekFlippedBuffer(buffer);
			long commitLevel = buffer.getLong();
			return new Packet_MutationEntityFromClient(mutation, commitLevel);
		};
	}


	public final EntityChangeTopLevelMovement<IMutablePlayerEntity> mutation;
	public final long commitLevel;

	public Packet_MutationEntityFromClient(EntityChangeTopLevelMovement<IMutablePlayerEntity> mutation, long commitLevel)
	{
		super(TYPE);
		
		this.mutation = mutation;
		this.commitLevel = commitLevel;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		EntityActionCodec.serializeToBuffer(buffer, this.mutation);
		buffer.putLong(this.commitLevel);
	}
}
