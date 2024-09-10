package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.CuboidAddress;


/**
 * Sent before the cuboid data in order to provide basic cuboid information (address or other small meta-data) and to
 * put the receiver into a state where it can process the following CUBOID_FRAGMENT packets.
 */
public class Packet_CuboidStart extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.CUBOID_START;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			CuboidAddress address = CodecHelpers.readCuboidAddress(buffer);
			return new Packet_CuboidStart(address);
		};
	}


	public final CuboidAddress address;

	public Packet_CuboidStart(CuboidAddress address)
	{
		super(TYPE);
		this.address = address;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeCuboidAddress(buffer, this.address);
	}
}
