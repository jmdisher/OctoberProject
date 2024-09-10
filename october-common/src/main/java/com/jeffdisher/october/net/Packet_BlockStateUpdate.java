package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.MutationBlockSetBlock;


/**
 * Contains a MutationBlockSetBlock instance.
 * Note that these only travel from server to client.
 */
public class Packet_BlockStateUpdate extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.BLOCK_STATE_UPDATE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			MutationBlockSetBlock stateUpdate = MutationBlockSetBlock.deserializeFromBuffer(buffer);
			return new Packet_BlockStateUpdate(stateUpdate);
		};
	}


	public final MutationBlockSetBlock stateUpdate;

	public Packet_BlockStateUpdate(MutationBlockSetBlock stateUpdate)
	{
		super(TYPE);
		this.stateUpdate = stateUpdate;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		this.stateUpdate.serializeToBuffer(buffer);
	}
}
