package com.jeffdisher.october.data;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;


public class TestOctreeBlock
{
	@Test
	public void filled()
	{
		OctreeShort test = OctreeShort.create(BlockAspect.AIR);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)31, (byte)31, (byte)31));
	}

	@Test
	public void update()
	{
		OctreeShort test = OctreeShort.create(BlockAspect.AIR);
		// Change one value to cause the tree to split.
		test.setData((byte)5, (byte)6, (byte)7, BlockAspect.STONE);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals(BlockAspect.STONE, (short)test.getData(BlockAspect.BLOCK, (byte)5, (byte)6, (byte)7));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)31, (byte)31, (byte)31));
		
		// Change it back, causing it to coalesce.
		test.setData((byte)5, (byte)6, (byte)7, BlockAspect.AIR);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)0, (byte)0, (byte)0));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)5, (byte)6, (byte)7));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, (byte)31, (byte)31, (byte)31));
	}
}
