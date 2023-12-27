package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.types.CuboidAddress;


/**
 * Send a cuboid's short aspect data plane, or at least part of it.  Most cuboids only need to send one but the
 * worst-case short data is roughly 71 kB, so it can't fit in one packet, hence multiple may be used.
 */
public class Packet_CuboidSetAspectShortPart extends Packet
{
	public static final PacketType TYPE = PacketType.CUBOID_SET_ASPECT_SHORT_PART;

	public static void register(Function<ByteBuffer, Packet>[] opcodeTable)
	{
		opcodeTable[TYPE.ordinal()] = (ByteBuffer buffer) -> {
			// First, read the cuboid address.
			CuboidAddress address = CodecHelpers.readCuboidAddress(buffer);
			// Then, load the aspect index.
			int index = buffer.getInt();
			// Read the total size of the aspect which is eventually coming.
			int totalSize = buffer.getInt();
			// Read the fragment of the aspect we received.
			byte[] payload = CodecHelpers.readBytes(buffer);
			return new Packet_CuboidSetAspectShortPart(address, index, totalSize, payload);
		};
	}

	public static Packet_CuboidSetAspectShortPart[] fragmentData(CuboidAddress cuboidAddress, int aspectIndex, byte[] completeAspectPlane)
	{
		// Note that we want to fit this into 1 packet, where possible, and generally saturate where we can so find the limit of what we can put here.
		// Our packet overhead is:  19 = header(3) + address(6=2*3) + index(4) + total(4) + aspectData(2 (size)).
		int completePreambleBytes = 19;
		int maxPayloadPerFragment = PacketCodec.MAX_PACKET_BYTES - completePreambleBytes;
		Packet_CuboidSetAspectShortPart[] parts;
		if (completeAspectPlane.length > maxPayloadPerFragment)
		{
			// Split.
			parts = new Packet_CuboidSetAspectShortPart[2];
			
			int totalSize = completeAspectPlane.length;
			byte[] fragment1 = new byte[PacketCodec.MAX_PACKET_BYTES - completePreambleBytes];
			System.arraycopy(completeAspectPlane, 0, fragment1, 0, fragment1.length);
			byte[] fragment2 = new byte[totalSize - fragment1.length];
			System.arraycopy(completeAspectPlane, fragment1.length, fragment2, 0, fragment2.length);
			
			parts[0] = new Packet_CuboidSetAspectShortPart(cuboidAddress, aspectIndex, totalSize, fragment1);
			parts[1] = new Packet_CuboidSetAspectShortPart(cuboidAddress, aspectIndex, totalSize, fragment2);
		}
		else
		{
			// Single.
			parts = new Packet_CuboidSetAspectShortPart[1];
			int totalSize = completeAspectPlane.length;
			parts[0] = new Packet_CuboidSetAspectShortPart(cuboidAddress, aspectIndex, totalSize, completeAspectPlane);
		}
		return parts;
	}

	public static byte[] stitchPlanes(Packet_CuboidSetAspectShortPart[] planes)
	{
		byte[] completeAspectPlane = new byte[planes[0].totalSize];
		int writeIndex = 0;
		for (Packet_CuboidSetAspectShortPart part : planes)
		{
			System.arraycopy(part.aspectData, 0, completeAspectPlane, writeIndex, part.aspectData.length);
			writeIndex += part.aspectData.length;
		}
		return completeAspectPlane;
	}


	public final CuboidAddress cuboidAddress;
	public final int aspectIndex;
	public final int totalSize;
	public final byte[] aspectData;

	public Packet_CuboidSetAspectShortPart(CuboidAddress cuboidAddress, int aspectIndex, int totalSize, byte[] aspectData)
	{
		super(TYPE);
		this.cuboidAddress = cuboidAddress;
		this.aspectIndex = aspectIndex;
		this.totalSize = totalSize;
		this.aspectData = aspectData;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeCuboidAddress(buffer, this.cuboidAddress);
		buffer.putInt(this.aspectIndex);
		buffer.putInt(this.totalSize);
		CodecHelpers.writeBytes(buffer, this.aspectData);
	}
}
