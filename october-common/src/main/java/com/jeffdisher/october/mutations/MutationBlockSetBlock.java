package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;


/**
 * Completely replaces all aspects of a given block.
 */
public class MutationBlockSetBlock implements IBlockStateUpdate
{
	public static final BlockStateUpdateType TYPE = BlockStateUpdateType.SET_BLOCK;

	public static MutationBlockSetBlock deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		byte[] rawData = new byte[buffer.remaining()];
		buffer.get(rawData);
		return new MutationBlockSetBlock(location, rawData);
	}

	/**
	 * Creates the set block mutation from the data held within a mutable proxy.
	 * Note that scratchBuffer is provided to avoid allocating a large buffer for every one of these, since they are
	 * common in the inner loop.
	 * 
	 * @param scratchBuffer A reusable scratch buffer which should be large enough to contain the entire serialized data
	 * from the proxy.
	 * @param proxy The proxy to extract.
	 * @return The set block mutation describing the final state of the proxy.
	 */
	public static MutationBlockSetBlock extractFromProxy(ByteBuffer scratchBuffer, MutableBlockProxy proxy)
	{
		scratchBuffer.clear();
		proxy.serializeToBuffer(scratchBuffer);
		scratchBuffer.flip();
		byte[] rawData = new byte[scratchBuffer.remaining()];
		scratchBuffer.get(rawData);
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
	public void applyState(IMutableBlockProxy newBlock)
	{
		// We want to decode the raw data as we feed it in to the proxy.
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		newBlock.deserializeFromBuffer(buffer);
	}

	@Override
	public BlockStateUpdateType getType()
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
