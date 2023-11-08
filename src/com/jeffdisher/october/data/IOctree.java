package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Aspect;


public interface IOctree
{
	<T> T getData(Aspect<T> type, byte x, byte y, byte z);
	<T> void setData(byte x, byte y, byte z, T value);
	void serialize(ByteBuffer buffer, IAspectCodec<?> codec);
	IOctree cloneData();
}
