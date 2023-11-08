package com.jeffdisher.october.data;

import java.nio.ByteBuffer;


public interface IAspectCodec<T>
{
	T loadData(ByteBuffer buffer);
	void storeData(ByteBuffer buffer, Object object);
}
