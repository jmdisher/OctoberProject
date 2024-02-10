package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.PacketCodec;
import com.jeffdisher.october.registries.AspectRegistry;
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
		// TODO: See if we get a win from pushing this buffer up higher in the stack since it is big and the JIT may not be able to help us.
		ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.MAX_PACKET_BYTES - PacketCodec.HEADER_BYTES);
		for (int i = 0; i < AspectRegistry.ALL_ASPECTS.length; ++i)
		{
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			// We want to honour the mutable proxy's internal type-specific data so check this type.
			if (Short.class == type.type())
			{
				// We just checked this.
				@SuppressWarnings("unchecked")
				short value = proxy.getData15((Aspect<Short, ?>) type);
				buffer.putShort(value);
			}
			else if (Byte.class == type.type())
			{
				// We just checked this.
				@SuppressWarnings("unchecked")
				byte value = proxy.getData7((Aspect<Byte, ?>) type);
				buffer.put(value);
			}
			else
			{
				Object value = proxy.getDataSpecial(type);
				type.codec().storeData(buffer, value);
			}
		}
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

	// We check these types internally.
	@SuppressWarnings("unchecked")
	@Override
	public boolean applyMutation(TickProcessingContext context, MutableBlockProxy newBlock)
	{
		// We want to decode the raw data as we feed it in to the proxy.
		ByteBuffer buffer = ByteBuffer.wrap(_rawData);
		for (int i = 0; i < AspectRegistry.ALL_ASPECTS.length; ++i)
		{
			Aspect<?, ?> type = AspectRegistry.ALL_ASPECTS[i];
			if (Short.class == type.type())
			{
				short value = buffer.getShort();
				newBlock.setData15((Aspect<Short, ?>) type, value);
			}
			else if (Byte.class == type.type())
			{
				byte value = buffer.get();
				newBlock.setData7((Aspect<Byte, ?>) type, value);
			}
			else
			{
				_readAndStore(type, newBlock, buffer);
			}
		}
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

	private <T> void _readAndStore(Aspect<T, ?> type, MutableBlockProxy newBlock, ByteBuffer buffer)
	{
		T value = type.codec().loadData(buffer);
		newBlock.setDataSpecial(type, value);
	}
}
