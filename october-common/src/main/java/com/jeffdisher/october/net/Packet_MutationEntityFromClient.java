package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains a specific IMutationEntity instance.
 * This is for the case when a client is sending its mutations so there is no ID as the server knows who they are.
 */
public class Packet_MutationEntityFromClient extends PacketFromClient
{
	public static final PacketType TYPE = PacketType.MUTATION_ENTITY_FROM_CLIENT;
	/**
	 * The white-list of entity changes which can come directly from a client (since some are internal-only).
	 */
	public static final Set<MutationEntityType> ALLOWED_TYPES = Arrays.stream(new MutationEntityType[] {
		MutationEntityType.MOVE,
		MutationEntityType.JUMP,
		MutationEntityType.SWIM,
		MutationEntityType.BLOCK_PLACE,
		MutationEntityType.CRAFT,
		MutationEntityType.SELECT_ITEM,
		MutationEntityType.ITEMS_REQUEST_PUSH,
		MutationEntityType.ITEMS_REQUEST_PULL,
		MutationEntityType.INCREMENTAL_BREAK_BLOCK,
		MutationEntityType.CRAFT_IN_BLOCK,
		MutationEntityType.ATTACK_ENTITY,
		MutationEntityType.USE_SELECTED_ITEM_ON_SELF,
		MutationEntityType.USE_SELECTED_ITEM_ON_BLOCK,
		MutationEntityType.USE_SELECTED_ITEM_ON_ENTITY,
		MutationEntityType.CHANGE_HOTBAR_SLOT,
		MutationEntityType.SWAP_ARMOUR,
		MutationEntityType.SET_BLOCK_LOGIC_STATE,
		MutationEntityType.SET_DAY_AND_SPAWN,
		MutationEntityType.SET_ORIENTATION,
		MutationEntityType.ACCELERATE,
		MutationEntityType.INCREMENTAL_REPAIR_BLOCK,
		MutationEntityType.MULTI_BLOCK_PLACE,
		MutationEntityType.TIME_SYNC_NOOP,
	}).collect(Collectors.toSet());

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			IMutationEntity<IMutablePlayerEntity> mutation = MutationEntityCodec.parseAndSeekFlippedBuffer(buffer);
			long commitLevel = buffer.getLong();
			return new Packet_MutationEntityFromClient(mutation, commitLevel);
		};
	}


	public final IMutationEntity<IMutablePlayerEntity> mutation;
	public final long commitLevel;

	public Packet_MutationEntityFromClient(IMutationEntity<IMutablePlayerEntity> mutation, long commitLevel)
	{
		super(TYPE);
		
		// We can only send mutations over the wire which are the kind which can actively originate within the client (not internal state changing calls).
		Assert.assertTrue(ALLOWED_TYPES.contains(mutation.getType()));
		
		this.mutation = mutation;
		this.commitLevel = commitLevel;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		MutationEntityCodec.serializeToBuffer(buffer, this.mutation);
		buffer.putLong(this.commitLevel);
	}
}
