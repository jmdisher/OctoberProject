package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


public interface IOctree
{
	<T> T getData(Aspect<T> type, BlockAddress address);
	<T> void setData(BlockAddress address, T value);
	void serialize(ByteBuffer buffer, IAspectCodec<?> codec);
	IOctree cloneData();
}
