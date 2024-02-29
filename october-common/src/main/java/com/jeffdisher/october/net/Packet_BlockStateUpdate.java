package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.mutations.IBlockStateUpdate;


/**
 * Contains a specific IBlockStateUpdate instance.
 * Note that these only travel from server to client.
 */
public class Packet_BlockStateUpdate extends Packet
{
	public static final PacketType TYPE = PacketType.BLOCK_STATE_UPDATE;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			IBlockStateUpdate stateUpdate = BlockStateUpdateCodec.parseAndSeekFlippedBuffer(buffer);
			return new Packet_BlockStateUpdate(stateUpdate);
		};
	}


	public final IBlockStateUpdate stateUpdate;

	public Packet_BlockStateUpdate(IBlockStateUpdate stateUpdate)
	{
		super(TYPE);
		this.stateUpdate = stateUpdate;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		BlockStateUpdateCodec.serializeToBuffer(buffer, this.stateUpdate);
	}
}
