package com.jeffdisher.october.data;

import org.junit.Assert;
import org.junit.Test;


public class TestOctreeShort
{
	public static final Aspect<Short> ASPECT_SHORT = new Aspect<>("Short", 0, Short.class);

	@Test
	public void filled()
	{
		OctreeShort test = OctreeShort.create((short)1);
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)31, (byte)31, (byte)31));
	}

	@Test
	public void update()
	{
		OctreeShort test = OctreeShort.create((short)1);
		// Change one value to cause the tree to split.
		test.setData((byte)5, (byte)6, (byte)7, (short)2);
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals((short)2, (short)test.getData(ASPECT_SHORT, (byte)5, (byte)6, (byte)7));
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)31, (byte)31, (byte)31));
		
		// Change it back, causing it to coalesce.
		test.setData((byte)5, (byte)6, (byte)7, (short)1);
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)5, (byte)6, (byte)7));
		Assert.assertEquals((short)1, (short)test.getData(ASPECT_SHORT, (byte)31, (byte)31, (byte)31));
	}
}
