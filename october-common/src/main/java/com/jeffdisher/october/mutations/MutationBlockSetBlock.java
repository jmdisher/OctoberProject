package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Completely replaces all aspects of a given block.
 */
public class MutationBlockSetBlock implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.SET_BLOCK;

	public static MutationBlockSetBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		return new MutationBlockSetBlock(location, rawData);
	}

	public static MutationBlockSetBlock extractFromProxy(MutableBlockProxy proxy)
	{
		ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES - PacketCodec.HEADER_BYTES);
		proxy.serializeToBuffer(buffer);
		buffer.flip();
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		return new MutationBlockSetBlock(proxy.absoluteLocation, rawData);
	}


	private final AbsoluteLocation _location;
	private final byte[] _rawData;

	public MutationBlockSetBlock(AbsoluteLocation location, byte[] rawData)
	{
		_location = location;
		_rawData = rawData;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		// We want to decode the raw data as we feed it in to the proxy.
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		newBlock.deserializeFromBuffer(buffer);
		return true;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		buffer.put(_rawData);
	}
}
