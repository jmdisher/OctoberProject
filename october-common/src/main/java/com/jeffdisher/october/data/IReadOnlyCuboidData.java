package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public interface IReadOnlyCuboidData
{
	CuboidAddress getCuboidAddress();
	byte getData7(Aspect<Byte, ?> type, BlockAddress address);
	short getData15(Aspect<Short, ?> type, BlockAddress address);
	<T> T getDataSpecial(Aspect<T, ?> type, BlockAddress address);
	Object serializeResumable(Object lastCallState, ByteBuffer buffer);
}
