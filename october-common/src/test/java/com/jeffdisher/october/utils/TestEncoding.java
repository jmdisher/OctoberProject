package com.jeffdisher.october.utils;

import org.junit.Assert;
import org.junit.Test;


public class TestEncoding
{
	@Test
	public void addressSplitting()
	{
		int absolute = 0x0017ABC;
		short expectedCuboid = (short)0x0BD5;
		byte expectedBlock = (byte)0x1C;
		
		Assert.assertEquals(expectedCuboid, Encoding.getCuboidAddress(absolute));
		Assert.assertEquals(expectedBlock, Encoding.getBlockAddress(absolute));
		
		int combined = (expectedCuboid << Encoding.CUBOID_SHIFT) | expectedBlock;
		Assert.assertEquals(absolute, combined);
	}
}
