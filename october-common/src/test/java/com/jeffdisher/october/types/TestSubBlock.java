package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;


public class TestSubBlock
{
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
