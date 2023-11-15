package com.jeffdisher.october.utils;

import java.util.HashSet;

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
		
		Assert.assertFalse(Encoding.checkShortTag(zero));
		Assert.assertFalse(Encoding.checkShortTag(one));
		Assert.assertFalse(Encoding.checkShortTag(big));
		Assert.assertTrue(Encoding.checkShortTag(tagZero));
		Assert.assertTrue(Encoding.checkShortTag(tagOne));
		Assert.assertTrue(Encoding.checkShortTag(tagBig));
		Assert.assertEquals(zero, Encoding.clearShortTag(tagZero));
		Assert.assertEquals(one, Encoding.clearShortTag(tagOne));
		Assert.assertEquals(big, Encoding.clearShortTag(tagBig));
	}

	@Test
	public void hashes()
	{
		HashSet<Long> set = new HashSet<>();
		set.add(Encoding.encodeCuboidAddress( (short) 0, (short) 0, (short) 0 ));
		set.add(Encoding.encodeCuboidAddress( (short) 0, (short) 0, (short) -1 ));
		set.add(Encoding.encodeCuboidAddress( (short) 0, (short) -1, (short) 0 ));
		set.add(Encoding.encodeCuboidAddress( (short) 0, (short) -1, (short) -1 ));
		set.add(Encoding.encodeCuboidAddress( (short) -1, (short) 0, (short) 0 ));
		set.add(Encoding.encodeCuboidAddress( (short) -1, (short) 0, (short) -1 ));
		set.add(Encoding.encodeCuboidAddress( (short) -1, (short) -1, (short) 0 ));
		set.add(Encoding.encodeCuboidAddress( (short) -1, (short) -1, (short) -1 ));
		Assert.assertEquals(8, set.size());
	}
}
