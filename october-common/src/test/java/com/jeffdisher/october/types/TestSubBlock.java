package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;


public class TestSubBlock
{
	@Test
	public void basics()
	{
		SubBlock sub1 = SubBlock.base(new EntityLocation(0.1f, 1.3f, 2.7f));
		SubBlock sub2 = SubBlock.fromInt(0, 1, 2);
		SubBlock sub3 = SubBlock.base(new EntityLocation(0.1f, 1.8f, 2.7f));
		Assert.assertEquals(sub1, sub2);
		Assert.assertEquals(sub1.hashCode(), sub2.hashCode());
		Assert.assertNotEquals(sub1, sub3);
		Assert.assertNotEquals(sub1.hashCode(), sub3.hashCode());
		Assert.assertEquals("SubBlock(0, 1, 2)", sub1.toString());
	}

	@Test
	public void positiveLow()
	{
		EntityLocation location = new EntityLocation(0.1f, 1.3f, 2.7f);
		SubBlock sub = SubBlock.base(location);
		Assert.assertEquals(0, sub.x);
		Assert.assertEquals(1, sub.y);
		Assert.assertEquals(2, sub.z);
	}

	@Test
	public void positiveHigh()
	{
		EntityLocation location = new EntityLocation(0.76f, 1.88f, 2.99f);
		SubBlock sub = SubBlock.base(location);
		Assert.assertEquals(3, sub.x);
		Assert.assertEquals(3, sub.y);
		Assert.assertEquals(3, sub.z);
	}

	@Test
	public void negativeLow()
	{
		EntityLocation location = new EntityLocation(-0.7f, -1.88f, -2.99f);
		SubBlock sub = SubBlock.base(location);
		Assert.assertEquals(1, sub.x);
		Assert.assertEquals(0, sub.y);
		Assert.assertEquals(0, sub.z);
	}

	@Test
	public void negativeHigh()
	{
		EntityLocation location = new EntityLocation(-0.1f, -1.3f, -2.7f);
		SubBlock sub = SubBlock.base(location);
		Assert.assertEquals(3, sub.x);
		Assert.assertEquals(2, sub.y);
		Assert.assertEquals(1, sub.z);
	}
}
