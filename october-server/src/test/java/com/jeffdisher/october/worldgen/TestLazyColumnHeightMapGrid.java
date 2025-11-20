package com.jeffdisher.october.worldgen;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class TestLazyColumnHeightMapGrid
{
	@Test
	public void internals() throws Throwable
	{
		int seed = 42;
		Assert.assertEquals(BlockAddress.fromInt(5, 9, 0), LazyColumnHeightMapGrid.test_getCentre(seed, (short)0, (short)0));
		Assert.assertEquals(7, LazyColumnHeightMapGrid.test_getRawPeak(seed, (short)0, (short)0));
		Assert.assertEquals(7, LazyColumnHeightMapGrid.test_getAdjustedPeak(seed, (short)0, (short)0));
		ColumnHeightMap heightMap = LazyColumnHeightMapGrid.test_getHeightMap(seed, (short)0, (short)0);
		Assert.assertEquals(8, heightMap.getHeight(0, 0));
	}

	@Test
	public void renderRegion() throws Throwable
	{
		int seed = 42;
		ColumnHeightMap[][] region = new ColumnHeightMap[3][3];
		for (short y = -1; y <= 1; ++y)
		{
			for (short x = -1; x <= 1; ++x)
			{
				region[y + 1][x + 1] = LazyColumnHeightMapGrid.test_getHeightMap(seed, x, y);
			}
		}
		for (int y = 63; y >= -32; --y)
		{
			for (int x = -32; x < 64; ++x)
			{
				AbsoluteLocation loc = new AbsoluteLocation(x, y, 0);
				CuboidAddress cuboid = loc.getCuboidAddress();
				BlockAddress block = loc.getBlockAddress();
				System.out.print(Integer.toHexString(region[cuboid.y() + 1][cuboid.x() + 1].getHeight(block.x(), block.y())));
			}
			System.out.println();
		}
	}
}
