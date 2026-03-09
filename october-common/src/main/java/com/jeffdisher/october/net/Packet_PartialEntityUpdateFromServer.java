package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Contains a specific PartialEntityUpdate instance.
 */
public class Packet_PartialEntityUpdateFromServer extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.PARTIAL_ENTITY_UPDATE_FROM_SERVER;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			PartialEntityUpdate update = PartialEntityUpdate.deserializeFromNetworkBuffer(buffer);
			return new Packet_PartialEntityUpdateFromServer(update);
		};
	}


	public final PartialEntityUpdate update;

	public Packet_PartialEntityUpdateFromServer(PartialEntityUpdate update)
	{
		super(TYPE);
		this.update = update;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		this.update.serializeToNetworkBuffer(buffer);
	}
}
