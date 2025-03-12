package com.jeffdisher.october.worldgen;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.logic.CreatureIdAssigner;
import com.jeffdisher.october.persistence.SuspendedCuboid;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


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
		Assert.assertEquals(BlockAddress.fromInt(5, 9, 0), generator.test_getCentre((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getRawPeak((short)0, (short)0));
		Assert.assertEquals(7, generator.test_getAdjustedPeak((short)0, (short)0));
		ColumnHeightMap heightMap = generator.test_getHeightMap((short)0, (short)0);
		Assert.assertEquals(8, heightMap.getHeight(0, 0));
	}

	@Test
	public void renderRegion() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		ColumnHeightMap[][] region = new ColumnHeightMap[3][3];
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
				System.out.print(Integer.toHexString(region[cuboid.y() + 1][cuboid.x() + 1].getHeight(block.x(), block.y())));
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
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(null, CuboidAddress.fromInt(0, 0, 0));
		CuboidData cuboid = suspended.cuboid();
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		short grassNumber = ENV.items.getItemById("op.grass").number();
		short dirtNumber = ENV.items.getItemById("op.dirt").number();
		short coalNumber = ENV.items.getItemById("op.coal_ore").number();
		short ironNumber = ENV.items.getItemById("op.iron_ore").number();
		short logNumber = ENV.items.getItemById("op.log").number();
		short leafNumber = ENV.items.getItemById("op.leaf").number();
		short airNumber = ENV.items.getItemById("op.air").number();
		// We will just verify that this has air above stone with a dirt layer between.
		int minDirt = Integer.MAX_VALUE;
		int maxDirt = 0;
		int grassCount = 0;
		int dirtCount = 0;
		int coalCount = 0;
		int ironCount = 0;
		int logCount = 0;
		int leafCount = 0;
		CuboidHeightMap heightMap = suspended.heightMap();
		for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				boolean foundDirt = false;
				byte highestNonAir = CuboidHeightMap.UNKNOWN_HEIGHT;
				for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
				{
					short number = cuboid.getData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z));
					if (!foundDirt)
					{
						if (grassNumber == number)
						{
							foundDirt = true;
							minDirt = Math.min(minDirt, z);
							maxDirt = Math.max(maxDirt, z);
							grassCount += 1;
						}
						else if (dirtNumber == number)
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
						if (logNumber == number)
						{
							logCount += 1;
						}
						else if (leafNumber == number)
						{
							leafCount += 1;
						}
						else
						{
							Assert.assertEquals(airNumber, number);
						}
					}
					if (airNumber != number)
					{
						highestNonAir = z;
					}
				}
				Assert.assertEquals(highestNonAir, heightMap.getHightestSolidBlock(x, y));
			}
		}
		Assert.assertEquals(7, minDirt);
		Assert.assertEquals(9, maxDirt);
		Assert.assertEquals(Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - 18, grassCount);
		Assert.assertEquals(18, dirtCount);
		Assert.assertEquals(4, coalCount);
		Assert.assertEquals(0, ironCount);
		Assert.assertEquals(36, logCount);
		Assert.assertEquals(66, leafCount);
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
		CuboidAddress address = CuboidAddress.fromInt(5, 2, -1);
		CuboidData data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32642, 44, 82, 0, 0, 0, 0, 0, 0);
		
		// This one is at 0 so it should see some coal, but no iron.
		address = CuboidAddress.fromInt(5, 1, 0);
		data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32736, 32, 0, 0, 0, 0, 0, 0, 0);
		
		// This one is too deep for either.
		address = CuboidAddress.fromInt(5, 1, -5);
		data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		_checkBlockTypes(data, 32763, 0, 5, 0, 0, 0, 0, 0, 0);
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

	@Test
	public void forestCuboid() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(null, CuboidAddress.fromInt(-10, -9, 0));
		CuboidData cuboid = suspended.cuboid();
		int dirtBlocks = 18;
		_checkBlockTypes(cuboid, 6039, 16, 0, Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - dirtBlocks, dirtBlocks, 34, 69, 0, 0);
	}

	@Test
	public void gullyDepth() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		// These were identified experimentally.
		Assert.assertEquals(5, generator.test_getGullyDepth((short)-7, (short)6));
		Assert.assertEquals(0, generator.test_getGullyDepth((short)-6, (short)6));
	}

	@Test
	public void wheatField() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		// We know that this cuboid has a gully in a field biome so it will contain wheat.
		// (this will spawn cows so make sure we have an ID assigner).
		CreatureIdAssigner creatureIdAssigner = new CreatureIdAssigner();
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(creatureIdAssigner, CuboidAddress.fromInt(-10, 9, 0));
		
		// Verify the wheat field.
		CuboidData cuboid = suspended.cuboid();
		// Since there is a cavern here, we won't be completely covered with grass.
		int grassCount = 778;
		// This is a large field (56 in gully + 4).
		int blocksPlanted = 56 + 4;
		_checkBlockTypes(cuboid, 4873, 11, 0, grassCount, blocksPlanted, 0, 2, blocksPlanted, 0);
		
		// Verify that cows are spawned.
		List<CreatureEntity> creatures = suspended.creatures();
		Assert.assertEquals(5, creatures.size());
		Assert.assertEquals(new EntityLocation(-302.0f, 299.0f, 6.0f), creatures.get(0).location());
		Assert.assertEquals(new EntityLocation(-314.0f, 295.0f, 6.0f), creatures.get(1).location());
		Assert.assertEquals(new EntityLocation(-307.0f, 301.0f, 6.0f), creatures.get(2).location());
		Assert.assertEquals(new EntityLocation(-312.0f, 298.0f, 6.0f), creatures.get(3).location());
		Assert.assertEquals(new EntityLocation(-316.0f, 313.0f, 6.0f), creatures.get(4).location());
	}

	@Test
	public void carrotField() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		// We know that this cuboid has a gully in a meadow biome so it will contain carrots.
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(null, CuboidAddress.fromInt(-9, -5, 0));
		CuboidData cuboid = suspended.cuboid();
		// This is a small field (3 in gully + 3).
		int dirtBlocks = 6;
		_checkBlockTypes(cuboid, 9129, 0, 0, Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - dirtBlocks, dirtBlocks, 0, 0, 0, 3 + 3);
	}

	@Test
	public void stoneForest() throws Throwable
	{
		// This test verifies that we don't fail when we try to generate a forest on a stone peak (experimentally found this seed).
		int seed = 10256;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(null, CuboidAddress.fromInt(1, -1, 0));
		CuboidData cuboid = suspended.cuboid();
		int dirtBlocks = 17;
		_checkBlockTypes(cuboid, 11918, 24, 1, Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - 10 - dirtBlocks, dirtBlocks, 34, 69, 0, 0);
	}

	@Test
	public void testExtraOre() throws Throwable
	{
		// Show what happens with the extra iron ore spawns in high/low cuboids.
		Block stoneBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		
		// Try one high in the air.
		CuboidAddress address = CuboidAddress.fromInt(5, 2, 100);
		CuboidData data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		// We generate at least 1 ore.
		int iron = 1;
		_checkBlockTypes(data, 32767, 0, iron, 0, 0, 0, 0, 0, 0);
		
		// Try one deep underground.
		address = CuboidAddress.fromInt(5, 1, -200);
		data = CuboidGenerator.createFilledCuboid(address, stoneBlock);
		generator.test_generateOreNodes(address, data);
		// We generate at most 100 ore.
		iron = 100;
		_checkBlockTypes(data, 32668, 0, iron, 0, 0, 0, 0, 0, 0);
	}

	@Test
	public void mantleBarrier() throws Throwable
	{
		// Show the transition between the crust and the mantle.
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		
		// Verify the surface shape.
		CuboidAddress highAddress = CuboidAddress.fromInt(-1, 2, 0);
		CuboidData highData = generator.generateCuboid(null, highAddress).cuboid();
		
		// Verify the transition shape.
		CuboidAddress lowAddress = CuboidAddress.fromInt(-1, 2, -3);
		CuboidData lowData = generator.generateCuboid(null, lowAddress).cuboid();
		
		// We expect that the surface lines up with the mantle.
		Assert.assertEquals("SSSSSSSSGAAAAAAAAAAAAAAAAAAAAAAA", _coreSample(highData, 5, 22));
		Assert.assertEquals("LLLLBSSSSSSSSSSSSSSSSSSSSSSSSSSS", _coreSample(lowData, 5, 22));
		Assert.assertEquals("SSSSSSSSSGAAAAAAAAAAAAAAAAAAAAAA", _coreSample(highData, 9, 1));
		Assert.assertEquals("LLLLLBSSSSSSSSSSSSSSSSSSSSSSSSSS", _coreSample(lowData, 9, 1));
	}

	@Test
	public void randomFauna() throws Throwable
	{
		// We will test that some cows will spawn at random (based on cuboid seed), even if there is no gully.
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		// We know that this cuboid is a field so it will have some random wheat.
		// (this will spawn cows so make sure we have an ID assigner).
		CreatureIdAssigner creatureIdAssigner = new CreatureIdAssigner();
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(creatureIdAssigner, CuboidAddress.fromInt(1, -9, 0));
		
		// Verify that some wheat is present (numbers experimentally derived).
		CuboidData cuboid = suspended.cuboid();
		_checkBlockTypes(cuboid, 3242, 5, 0, Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - 4, 4, 0, 0, 4, 0);
		
		// Verify that cows are spawned.
		List<CreatureEntity> creatures = suspended.creatures();
		Assert.assertEquals(1, creatures.size());
		Assert.assertEquals(new EntityLocation(49.0f, -270.0f, 3.0f), creatures.get(0).location());
	}

	@Test
	public void caves() throws Throwable
	{
		// Tests that caves carve out some of the stone from the cuboid.
		int seed = 10256;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		Block stone = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		CuboidAddress address = CuboidAddress.fromInt(-100, -96, 3);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, stone);
		generator.test_carveOutCaves(cuboid);
		_checkBlockTypes(cuboid, 31148, 0, 0, 0, 0, 0, 0, 0, 0);
		
		// Verify that this cave isn't the same shape in any axis (bug due to poor random entropy).
		cuboid = CuboidGenerator.createFilledCuboid(address.getRelative(0, 0, 1), stone);
		generator.test_carveOutCaves(cuboid);
		_checkBlockTypes(cuboid, 28397, 0, 0, 0, 0, 0, 0, 0, 0);
		cuboid = CuboidGenerator.createFilledCuboid(address.getRelative(0, 1, 0), stone);
		generator.test_carveOutCaves(cuboid);
		_checkBlockTypes(cuboid, 30855, 0, 0, 0, 0, 0, 0, 0, 0);
		cuboid = CuboidGenerator.createFilledCuboid(address.getRelative(1, 0, 0), stone);
		generator.test_carveOutCaves(cuboid);
		_checkBlockTypes(cuboid, 32558, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	@Test
	public void surfaceCaves() throws Throwable
	{
		int seed = 42;
		BasicWorldGenerator generator = new BasicWorldGenerator(ENV, seed);
		CreatureIdAssigner creatureIdAssigner = new CreatureIdAssigner();
		// We know that this cuboid generates a surface cave.
		CuboidAddress address = CuboidAddress.fromInt(-12, 15, 0);
		SuspendedCuboid<CuboidData> suspended = generator.generateCuboid(creatureIdAssigner, address);
		CuboidData cuboid = suspended.cuboid();
		int grassCount = 662;
		_checkBlockTypes(cuboid, 7808, 4, 0, grassCount, 0, 0, 2, 0, 0);
	}


	private static void _checkBlockTypes(CuboidData data, int stone, int coal, int iron, int grass, int dirt, int log, int leaf, int wheat, int carrot)
	{
		short airNumber = ENV.items.getItemById("op.air").number();
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		short coalNumber = ENV.items.getItemById("op.coal_ore").number();
		short ironNumber = ENV.items.getItemById("op.iron_ore").number();
		short grassNumber = ENV.items.getItemById("op.grass").number();
		short dirtNumber = ENV.items.getItemById("op.dirt").number();
		short logNumber = ENV.items.getItemById("op.log").number();
		short leafNumber = ENV.items.getItemById("op.leaf").number();
		short wheatNumber = ENV.items.getItemById("op.wheat_mature").number();
		short carrotNumber = ENV.items.getItemById("op.carrot_mature").number();
		
		int stoneCount = 0;
		int coalCount = 0;
		int ironCount = 0;
		int grassCount = 0;
		int dirtCount = 0;
		int logCount = 0;
		int leafCount = 0;
		int wheatCount = 0;
		int carrotCount = 0;
		for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
			{
				for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
				{
					short value = data.getData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z));
					if (airNumber == value)
					{
						// We don't count the air.
					}
					else if (stoneNumber == value)
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
					else if (dirtNumber == value)
					{
						dirtCount += 1;
					}
					else if (grassNumber == value)
					{
						grassCount += 1;
					}
					else if (logNumber == value)
					{
						logCount += 1;
					}
					else if (leafNumber == value)
					{
						leafCount += 1;
					}
					else if (wheatNumber == value)
					{
						wheatCount += 1;
					}
					else if (carrotNumber == value)
					{
						carrotCount += 1;
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
		Assert.assertEquals(grass, grassCount);
		Assert.assertEquals(dirt, dirtCount);
		Assert.assertEquals(log, logCount);
		Assert.assertEquals(leaf, leafCount);
		Assert.assertEquals(wheat, wheatCount);
		Assert.assertEquals(carrot, carrotCount);
	}

	private static String _coreSample(CuboidData cuboid, int x, int y)
	{
		String sample = "";
		for (int i = 0; i < Encoding.CUBOID_EDGE_SIZE; ++i)
		{
			short value = cuboid.getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(x, y, i));
			char c = _getChar(value);
			sample += c;
		}
		return sample;
	}

	private static char _getChar(short value)
	{
		char c = '?';
		short airBlock = ENV.items.getItemById("op.air").number();
		short grassBlock = ENV.items.getItemById("op.grass").number();
		short dirtBlock = ENV.items.getItemById("op.dirt").number();
		short stoneBlock = ENV.items.getItemById("op.stone").number();
		short basaltBlock = ENV.items.getItemById("op.basalt").number();
		short lavaBlock = ENV.items.getItemById("op.lava_source").number();
		short leafBlock = ENV.items.getItemById("op.leaf").number();
		if (airBlock == value)
		{
			c = 'A';
		}
		else if (dirtBlock == value)
		{
			c = 'D';
		}
		else if (grassBlock == value)
		{
			c = 'G';
		}
		else if (stoneBlock == value)
		{
			c = 'S';
		}
		else if (basaltBlock == value)
		{
			c = 'B';
		}
		else if (lavaBlock == value)
		{
			c = 'L';
		}
		else if (leafBlock == value)
		{
			c = 'E';
		}
		return c;
	}
}
