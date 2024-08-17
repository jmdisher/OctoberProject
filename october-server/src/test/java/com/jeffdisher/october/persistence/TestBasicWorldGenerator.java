package com.jeffdisher.october.persistence;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.Structure;


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
		Assert.assertEquals(8, generator.test_getBiome((short)0, (short)0));
		Assert.assertEquals(new BlockAddress((byte)5, (byte)9, (byte)0), generator.test_getCentre((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getRawPeak((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getAdjustedPeak((short)0, (short)0));
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

	@Test
	public void biomeDistribution() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		int[] samples = new int[16];
		for (int i = 0; i < 10000; ++i)
		{
			int biome = generator.test_getBiome((short)i, (short)-i);
			samples[biome] += 1;
		}
		// This was collected experimentally.
		int[] expectedDistribution = new int[] {
				0,
				0,
				1,
				242,
				553,
				1198,
				1732,
				2368,
				1842,
				1149,
				600,
				306,
				9,
				0,
				0,
				0,
		};
		Assert.assertArrayEquals(expectedDistribution, samples);
	}

	@Test
	public void renderCuboid() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		SuspendedCuboid<CuboidData> suspended = generator.apply(null, new CuboidAddress((short)0, (short)0, (short)0));
		CuboidData cuboid = suspended.cuboid();
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		short dirtNumber = ENV.items.getItemById("op.dirt").number();
		short airNumber = ENV.items.getItemById("op.air").number();
		// We will just verify that this has air above stone with a dirt layer between.
		int minDirt = Integer.MAX_VALUE;
		int maxDirt = 0;
		int dirtCount = 0;
		for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
		{
			for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
			{
				boolean foundDirt = false;
				for (byte z = 0; z < Structure.CUBOID_EDGE_SIZE; ++z)
				{
					short number = cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z));
					if (!foundDirt)
					{
						if (dirtNumber == number)
						{
							foundDirt = true;
							minDirt = Math.min(minDirt, z);
							maxDirt = Math.max(maxDirt, z);
							dirtCount += 1;
						}
						else
						{
							Assert.assertEquals(stoneNumber, number);
						}
					}
					else
					{
						Assert.assertEquals(airNumber, number);
					}
				}
			}
		}
		Assert.assertEquals(7, minDirt);
		Assert.assertEquals(9, maxDirt);
		Assert.assertEquals(Structure.CUBOID_EDGE_SIZE * Structure.CUBOID_EDGE_SIZE, dirtCount);
	}

	@Test
	public void checkDefaultSpawn() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		EntityLocation spawn = generator.getDefaultSpawnLocation();
		// This expected value was verified by looking at the renderRegion() test output.
		// (a 9 is the greatest value in the centre cuboid and is first found by walking up along y in the first x location - then we add 1 to be on top).
		Assert.assertEquals(new EntityLocation(0.0f, 22.0f, 10.0f), spawn);
	}
}
