package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


/**
 * A proxy to access immutable block data within a specific cuboid snapshot.
 */
public class BlockProxy
{
	private final BlockAddress _address;
	private final IReadOnlyCuboidData _data;

	public BlockProxy(BlockAddress address, IReadOnlyCuboidData data)
	{
		_address = address;
		_data = data;
	}

	public byte getData7(Aspect<Byte, ?> type)
	{
		return _data.getData7(type, _address);
	}

	public short getData15(Aspect<Short, ?> type)
	{
		return _data.getData15(type, _address);
	}

	public <T> T getDataSpecial(Aspect<T, ?> type)
	{
		return _data.getDataSpecial(type, _address);
	}
}
