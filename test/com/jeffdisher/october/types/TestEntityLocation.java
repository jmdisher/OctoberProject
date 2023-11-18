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
}
