package com.jeffdisher.october.data;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.BlockAddress;


public class TestOctreeBlock
{
	@Test
	public void filled()
	{
		OctreeShort test = OctreeShort.create(BlockAspect.AIR);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)31, (byte)31, (byte)31)));
	}

	@Test
	public void update()
	{
		OctreeShort test = OctreeShort.create(BlockAspect.AIR);
		// Change one value to cause the tree to split.
		test.setData(new BlockAddress((byte)5, (byte)6, (byte)7), BlockAspect.STONE);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals(BlockAspect.STONE, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7)));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)31, (byte)31, (byte)31)));
		
		// Change it back, causing it to coalesce.
		test.setData(new BlockAddress((byte)5, (byte)6, (byte)7), BlockAspect.AIR);
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0)));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7)));
		Assert.assertEquals(BlockAspect.AIR, (short)test.getData(BlockAspect.BLOCK, new BlockAddress((byte)31, (byte)31, (byte)31)));
	}

	@Test
	public void worstCaseShort()
	{
		// Fill an octree with each possible value (32x32x32 is 32768).
		short value = 0;
		// We init to 0 and will set the first block to 0.
		OctreeShort test = OctreeShort.create(value);
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					test.setData(new BlockAddress((byte)x, (byte)y, (byte)z), value);
					value += 1;
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Store X: " + x + " took " + (end -start) + " millis");
		}
		
		test.getData(BlockAspect.BLOCK, new BlockAddress((byte)31, (byte)31, (byte)31));
		byte[] rawData = test.copyRawData();
		Assert.assertEquals(74898, rawData.length);
		OctreeShort verify = OctreeShort.load(ByteBuffer.wrap(rawData));
		
		// Verify that we can read all of these.
		value = 0;
		for (int x = 0; x < 32; ++x)
		{
			long start = System.currentTimeMillis();
			for (int y = 0; y < 32; ++y)
			{
				for (int z = 0; z < 32; ++z)
				{
					Assert.assertEquals(value, (short)verify.getData(BlockAspect.BLOCK, new BlockAddress((byte)x, (byte)y, (byte)z)));
					value += 1;
				}
			}
			long end = System.currentTimeMillis();
			System.out.println("Load X: " + x + " took " + (end -start) + " millis");
		}
	}
}
