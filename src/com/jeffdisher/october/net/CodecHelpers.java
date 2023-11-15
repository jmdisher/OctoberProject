package com.jeffdisher.october.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.october.utils.Assert;


public class CodecHelpers
{
	public static String readString(ByteBuffer buffer)
	{
		int length = Short.toUnsignedInt(buffer.getShort());
		byte[] data = new byte[length];
		buffer.get(data);
		return new String(data, StandardCharsets.UTF_8);
	}

	public static void writeString(ByteBuffer buffer, String value)
	{
		byte[] data = value.getBytes(StandardCharsets.UTF_8);
		int length = data.length;
		Assert.assertTrue(length <= Short.MAX_VALUE);
		buffer.putShort((short)length);
		buffer.put(data);
	}
}
