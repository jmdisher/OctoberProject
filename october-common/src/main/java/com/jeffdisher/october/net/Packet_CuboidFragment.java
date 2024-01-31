package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;


/**
 * Sends part of a cuboid's data.  The receiver should be ready to receive this data after receiving a CUBOID_START
 * packet and will know when it has received the final fragment based on the content.
 * Most cuboids only need to send one or these the worst-case short data is roughly 71 kB, and object data could also be
 * large, hence multiple may be used.
 */
public class Packet_CuboidFragment extends Packet
{
	public static final PacketType TYPE = PacketType.CUBOID_FRAGMENT;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// This packet is just raw data, interpreted by the cuboid data parser, so just copy what is available.
			// We only get called when the entire packet is available so we can take all of it.
			byte[] payload = new byte[buffer.remaining()];
			buffer.get(payload);
			return new Packet_CuboidFragment(payload);
		};
	}


	public final byte[] payload;

	public Packet_CuboidFragment(byte[] payload)
	{
		super(TYPE);
		this.payload = payload;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Just copy everything in the payload into the buffer.
		buffer.put(this.payload);
	}
}
