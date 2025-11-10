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

	@Test
	public void forcedRounding()
	{
		// Make sure that our forced rounding to .01 precision works for some positive and negative numbers.
		EntityLocation positive = new EntityLocation( 0.001f,  1.885f,  2.347f);
		Assert.assertEquals(0.00f, positive.x(), 0.0001f);
		Assert.assertEquals(1.89f, positive.y(), 0.0001f);
		Assert.assertEquals(2.35f, positive.z(), 0.0001f);
		
		EntityLocation negative = new EntityLocation(-0.001f, -1.885f, -2.347f);
		Assert.assertEquals( 0.00f, negative.x(), 0.0001f);
		Assert.assertEquals(-1.88f, negative.y(), 0.0001f);
		Assert.assertEquals(-2.35f, negative.z(), 0.0001f);
		
		Assert.assertEquals( 0.00f, EntityLocation.roundToHundredths(-0.001f), 0.0001f);
		Assert.assertEquals(-1.88f, EntityLocation.roundToHundredths(-1.885f), 0.0001f);
		Assert.assertEquals(-2.35f, EntityLocation.roundToHundredths(-2.347f), 0.0001f);
	}

	@Test
	public void magnitude()
	{
		Assert.assertEquals(0.0f, new EntityLocation(0.0f, 0.0f, 0.0f).getMagnitude(), 0.01f);
		Assert.assertEquals(1.0f, new EntityLocation(-1.0f, 0.0f, 0.0f).getMagnitude(), 0.01f);
		Assert.assertEquals(3.09f, new EntityLocation(-0.1f, -1.5f, -2.7f).getMagnitude(), 0.01f);
		Assert.assertEquals(1003.67f, new EntityLocation(10.0f, -5.6f, 1003.6f).getMagnitude(), 0.01f);
	}

	@Test
	public void scaledInstance()
	{
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), new EntityLocation(0.0f, 0.0f, 0.0f).makeScaledInstance(-1.5f));
		Assert.assertEquals(new EntityLocation(-25.6f, 0.0f, 0.0f), new EntityLocation(-1.0f, 0.0f, 0.0f).makeScaledInstance(25.6f));
		Assert.assertEquals(new EntityLocation(-0.04f, -0.6f, -1.08f), new EntityLocation(-0.1f, -1.5f, -2.7f).makeScaledInstance(0.4f));
		Assert.assertEquals(new EntityLocation(0.5f, -0.28f, 50.18f), new EntityLocation(10.0f, -5.6f, 1003.6f).makeScaledInstance(0.05f));
	}
}
