package com.jeffdisher.october.data;

import java.nio.ByteBuffer;


public class DamageAspectCodec implements IObjectCodec<Integer>
{
	@Override
	public Integer loadData(DeserializationContext context)
	{
		int value = context.buffer().getInt();
		return (0 != value)
			? value
			: null
		;
	}

	@Override
	public void storeData(ByteBuffer buffer, Integer object)
	{
		// We normally don't see nulls when saving but we do when creating BlockChangeDescription.
		int value = (null != object)
			? object.intValue()
			: 0
		;
		buffer.putInt(value);
	}
}
