package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.CuboidAddress;


public class Packet_RemoveCuboid extends PacketFromServer
{
	public static final PacketType TYPE = PacketType.REMOVE_CUBOID;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			CuboidAddress address = CodecHelpers.readCuboidAddress(buffer);
			return new Packet_RemoveCuboid(address);
		};
	}


	public final CuboidAddress address;

	public Packet_RemoveCuboid(CuboidAddress address)
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
