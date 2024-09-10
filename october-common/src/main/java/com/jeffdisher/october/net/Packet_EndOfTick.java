package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Contains the information to describe the end of a server-side logical tick.
 */
public class Packet_EndOfTick extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.END_OF_TICK;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			long tickNumber = buffer.getLong();
			long latestLocalCommitIncluded = buffer.getLong();
			return new Packet_EndOfTick(tickNumber, latestLocalCommitIncluded);
		};
	}


	public final long tickNumber;
	public final long latestLocalCommitIncluded;

	public Packet_EndOfTick(long tickNumber, long latestLocalCommitIncluded)
	{
		super(TYPE);
		this.tickNumber = tickNumber;
		this.latestLocalCommitIncluded = latestLocalCommitIncluded;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putLong(this.tickNumber);
		buffer.putLong(this.latestLocalCommitIncluded);
	}
}
