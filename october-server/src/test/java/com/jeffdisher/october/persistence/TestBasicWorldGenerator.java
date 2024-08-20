package com.jeffdisher.october.persistence;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.worldgen.CuboidGenerator;
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
		short coalNumber = ENV.items.getItemById("op.coal_ore").number();
		short ironNumber = ENV.items.getItemById("op.iron_ore").number();
		short airNumber = ENV.items.getItemById("op.air").number();
		// We will just verify that this has air above stone with a dirt layer between.
		int minDirt = Integer.MAX_VALUE;
		int maxDirt = 0;
		int dirtCount = 0;
		int coalCount = 0;
		int ironCount = 0;
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
						else if (coalNumber == number)
						{
							coalCount += 1;
						}
						else if (ironNumber == number)
						{
							ironCount += 1;
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
		Assert.assertEquals(4, coalCount);
		Assert.assertEquals(0, ironCount);
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

	@Test
	public void oreSpawns() throws Throwable
	{
		Block stoneBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		
		// This one is in the middle of the range so it should see some values.
		// -coal is a range of 70 with 4 tries of 8 blocks so we should average around 14.6/cuboid
		// -iron is a range of 90 with 2 tries of 27 blocks so we should average around 19.2/cuboid
		CuboidAddress address = new CuboidAddress((short)5, (short)2, (short)-1);
		CuboidData data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32721, 20, 27);
		
		// This one is at 0 so it should see some coal, but no iron.
		address = new CuboidAddress((short)5, (short)1, (short)0);
		data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32756, 12, 0);
		
		// This one is too deep for either.
		address = new CuboidAddress((short)5, (short)1, (short)-5);
		data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32768, 0, 0);
	}

	@Test
	public void buildBiomeMap() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		for (short y = -10; y <= 10; ++y)
		{
			for (short x = -10; x <= 10; ++x)
			{
				char code = generator.test_getBiomeCode(x, y);
				System.out.print(code);
			}
			System.out.println();
		}
	}


	private static void _checkBlockTypes(CuboidData data, int stone, int coal, int iron)
	{
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		short coalNumber = ENV.items.getItemById("op.coal_ore").number();
		short ironNumber = ENV.items.getItemById("op.iron_ore").number();
		
		int stoneCount = 0;
		int coalCount = 0;
		int ironCount = 0;
		for (byte z = 0; z < Structure.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
				{
					short value = data.getData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z));
					if (stoneNumber == value)
					{
						stoneCount += 1;
					}
					else if (coalNumber == value)
					{
						coalCount += 1;
					}
					else if (ironNumber == value)
					{
						ironCount += 1;
					}
					else
					{
						Assert.fail();
					}
				}
			}
		}
		
		Assert.assertEquals(stone, stoneCount);
		Assert.assertEquals(coal, coalCount);
		Assert.assertEquals(iron, ironCount);
	}
}
