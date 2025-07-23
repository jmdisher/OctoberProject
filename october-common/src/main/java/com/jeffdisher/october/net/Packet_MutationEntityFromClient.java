package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.EntityChangeTopLevelMovement;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
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
			Environment env = Environment.getShared();
			DeserializationContext context = new DeserializationContext(env
				, buffer
			);
			
			EntityChangeTopLevelMovement<IMutablePlayerEntity> mutation = (EntityChangeTopLevelMovement<IMutablePlayerEntity>) EntityActionCodec.parseAndSeekContext(context);
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
