package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.types.BlockAddress;


public interface IOctree
{
	<T, O extends IOctree> T getData(Aspect<T, O> type, BlockAddress address);
	<T> void setData(BlockAddress address, T value);
	void serialize(ByteBuffer buffer, IAspectCodec<?> codec);
}
