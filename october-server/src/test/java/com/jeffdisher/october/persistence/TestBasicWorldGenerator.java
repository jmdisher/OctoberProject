package com.jeffdisher.october.persistence;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestBasicWorldGenerator
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void internals() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		Assert.assertEquals(1353309739, generator.test_getCuboidSeed((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getBiome((short)0, (short)0));
		Assert.assertEquals(new BlockAddress((byte)5, (byte)9, (byte)0), generator.test_getCentre((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getPeak((short)0, (short)0));
		int[][] heightMap = generator.test_getHeightMap((short)0, (short)0);
		Assert.assertEquals(8, heightMap[0][0]);
	}

	@Test
	public void renderRegion() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		int[][][][] region = new int[3][3][][];
		for (short y = -1; y <= 1; ++y)
		{
			for (short x = -1; x <= 1; ++x)
			{
				region[y + 1][x + 1] = generator.test_getHeightMap(x, y);
			}
		}
		for (int y = 63; y >= -32; --y)
		{
			for (int x = -32; x < 64; ++x)
			{
				AbsoluteLocation loc = new AbsoluteLocation(x, y, 0);
				CuboidAddress cuboid = loc.getCuboidAddress();
				BlockAddress block = loc.getBlockAddress();
				System.out.print(Integer.toHexString(region[cuboid.y() + 1][cuboid.x() + 1][block.y()][block.x()]));
			}
			System.out.println();
		}
	}
}
