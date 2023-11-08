package com.jeffdisher.october.data;

import java.nio.ByteBuffer;


public interface IOctree
{
	<T> T getData(Aspect<T> type, byte x, byte y, byte z);
	<T> void setData(byte x, byte y, byte z, T value);
	void serialize(ByteBuffer buffer, IAspectCodec<?> codec);
	IOctree cloneData();
}
