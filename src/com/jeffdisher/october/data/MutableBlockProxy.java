package com.jeffdisher.october.data;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


/**
 * A proxy to access mutable block data within a specific cuboid which is currently being updated.
 */
public class MutableBlockProxy
{
	private final BlockAddress _address;
	private final CuboidData _data;

	public MutableBlockProxy(BlockAddress address, CuboidData data)
	{
		_address = address;
		_data = data;
	}

	public byte getData7(Aspect<Byte, ?> type)
	{
		return _data.getData7(type, _address);
	}

	public void setData7(Aspect<Byte, ?> type, byte value)
	{
		_data.setData7(type, _address, value);
	}

	public short getData15(Aspect<Short, ?> type)
	{
		return _data.getData15(type, _address);
	}

	public void setData15(Aspect<Short, ?> type, short value)
	{
		_data.setData15(type, _address, value);
	}

	public <T> T getDataSpecial(Aspect<T, ?> type)
	{
		return _data.getDataSpecial(type, _address);
	}

	public <T> void setDataSpecial(Aspect<T, ?> type, T value)
	{
		_data.setDataSpecial(type, _address, value);
	}
}
