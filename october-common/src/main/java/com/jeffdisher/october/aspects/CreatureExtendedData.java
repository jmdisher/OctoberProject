package com.jeffdisher.october.aspects;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the definitions of creature extended data and associated data.
 */
public class CreatureExtendedData
{
	/**
	 * This codec only ever stores null data.
	 */
	public static class NullCodec implements EntityType.IExtendedCodec
	{
		@Override
		public Object buildDefault()
		{
			return null;
		}
		@Override
		public Object read(ByteBuffer buffer)
		{
			byte header = buffer.get();
			Assert.assertTrue((byte)0 == header);
			return null;
		}
		@Override
		public void write(ByteBuffer buffer, Object extendedData)
		{
			Assert.assertTrue(null == extendedData);
			byte header = 0;
			buffer.put(header);
		}
	};
}
