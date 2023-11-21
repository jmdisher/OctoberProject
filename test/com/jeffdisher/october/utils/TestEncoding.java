package com.jeffdisher.october.utils;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;


public class TestEncoding
{
	@Test
	public void shortTags()
	{
		short zero = 0;
		short one = 1;
		short big = Short.MAX_VALUE;
		short tagZero = Encoding.setShortTag(zero);
		short tagOne = Encoding.setShortTag(one);
		short tagBig = Encoding.setShortTag(big);
		
		Assert.assertEquals((short)-32768, tagZero);
		Assert.assertEquals((short)-32767, tagOne);
		Assert.assertEquals((short)-1, tagBig);
		
		Assert.assertFalse(Encoding.checkTag(_getHighByte(zero)));
		Assert.assertFalse(Encoding.checkTag(_getHighByte(one)));
		Assert.assertFalse(Encoding.checkTag(_getHighByte(big)));
		Assert.assertTrue(Encoding.checkTag(_getHighByte(tagZero)));
		Assert.assertTrue(Encoding.checkTag(_getHighByte(tagOne)));
		Assert.assertTrue(Encoding.checkTag(_getHighByte(tagBig)));
		Assert.assertEquals(zero, Encoding.clearShortTag(tagZero));
		Assert.assertEquals(one, Encoding.clearShortTag(tagOne));
		Assert.assertEquals(big, Encoding.clearShortTag(tagBig));
	}


	private static byte _getHighByte(short value)
	{
		return ByteBuffer.allocate(Short.BYTES).putShort(value).flip().get();
	}
}
