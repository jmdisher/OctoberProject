package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * An operation which over-writes part of the state of a single block.  This may be the entire block state, a single
 * aspect, or part of an aspect.
 * Note that these operations are considered idempotent and should blindly write data, not basing that on any existing
 * state.
 */
public class MutationBlockSetBlock
{
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

	/**
	 * @return The absolute coordinates of the block to which the mutation applies.
	 */
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	/**
	 * Applies the change to the given target.
	 * 
	 * @param target The cuboid to modify by writing the receiver's changes.
	 */
	@SuppressWarnings("unchecked")
	public void applyState(CuboidData target)
	{
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		BlockAddress location = _location.getBlockAddress();
		// We only store the data which actually changed so loop until the buffer segment is empty, checking for indices (byte).
		while (buffer.hasRemaining())
		{
			byte i = buffer.get();
			Assert.assertTrue(i >= 0);
			Assert.assertTrue(i < AspectRegistry.ALL_ASPECTS.length);
			
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			if (Short.class == type.type())
			{
				short value = buffer.getShort();
				target.setData15((Aspect<Short, ?>) type, location, value);
			}
			else if (Byte.class == type.type())
			{
				byte value = buffer.get();
				target.setData7((Aspect<Byte, ?>) type, location, value);
			}
			else
			{
				_readAndStore(target, location, type, buffer);
			}
		}
	}

	/**
	 * Called during serialization to serialize any internal instance variables of the state update to the given buffer.
	 * 
	 * @param buffer The buffer where the state update should be written.
	 */
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		buffer.put(_rawData);
	}


	private <T> void _readAndStore(CuboidData target, BlockAddress location, Aspect<T, ?> type, ByteBuffer buffer)
	{
		T value = type.codec().loadData(buffer);
		target.setDataSpecial((Aspect<T, ?>) type, location, value);
	}
}
