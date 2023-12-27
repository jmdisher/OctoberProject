package com.jeffdisher.october.types;

import org.junit.Assert;
import org.junit.Test;


public class TestEntityLocation
{
	@Test
	public void absolutePositive()
	{
		EntityLocation location = new EntityLocation(0.1f, 1.5f, 2.7f);
		AbsoluteLocation block = location.getBlockLocation();
		Assert.assertEquals(new AbsoluteLocation(0, 1, 2), block);
	}

	@Test
	public void absoluteNegative()
	{
		EntityLocation location = new EntityLocation(-0.1f, -1.5f, -2.7f);
		AbsoluteLocation block = location.getBlockLocation();
		Assert.assertEquals(new AbsoluteLocation(-1, -2, -3), block);
	}

	@Test
	public void offsetPositive()
	{
		EntityLocation location = new EntityLocation(0.1f, 1.5f, 2.7f);
		EntityLocation block = location.getOffsetIntoBlock();
		// These values can round oddly so use a delta.
		Assert.assertEquals(0.1f, block.x(), 0.01f);
		Assert.assertEquals(0.5f, block.y(), 0.01f);
		Assert.assertEquals(0.7f, block.z(), 0.01f);
	}

	@Test
	public void offsetNegative()
	{
		EntityLocation location = new EntityLocation(-0.1f, -1.5f, -2.7f);
		EntityLocation block = location.getOffsetIntoBlock();
		// These values can round oddly so use a delta.
		Assert.assertEquals(0.9f, block.x(), 0.01f);
		Assert.assertEquals(0.5f, block.y(), 0.01f);
		Assert.assertEquals(0.3f, block.z(), 0.01f);
	}
}
