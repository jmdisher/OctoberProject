package com.jeffdisher.october.logic;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.worldgen.CuboidGenerator;
import com.jeffdisher.october.worldgen.Structure;


public class TestHeightMapHelpers
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
	public void createUniformMap() throws Throwable
	{
		byte value = 5;
		byte[][] raw = HeightMapHelpers.createUniformHeightMap(value);
		for (byte[] row : raw)
		{
			for (byte one : row)
			{
				Assert.assertEquals(value, one);
			}
		}
	}

	@Test
	public void populateHeightMap() throws Throwable
	{
		// We will create a map which technically has the wrong values just to show the missing elements aren't updated.
		byte value = 5;
		byte[][] raw = HeightMapHelpers.createUniformHeightMap(value);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress blockAddress = new BlockAddress((byte)5, (byte)6, (byte)7);
		cuboid.setData15(AspectRegistry.BLOCK, blockAddress, ENV.items.getItemById("op.stone").number());
		HeightMapHelpers.populateHeightMap(raw, cuboid);
		for (int y = 0; y < raw.length; ++y)
		{
			byte[] row = raw[y];
			for (int x = 0; x < row.length; ++x)
			{
				byte one = row[x];
				if ((5 == x) && (6 == y))
				{
					Assert.assertEquals(7, one);
				}
				else
				{
					Assert.assertEquals(value, one);
				}
			}
		}
	}

	@Test
	public void buildHeightMap() throws Throwable
	{
		// Populate a cuboid with some values and observe the expected height map.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		for (byte x = 0; x < 2; ++x)
		{
			for (byte y = 0; y < 2; ++y)
			{
				for (byte z = 0; z < 2; ++z)
				{
					cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress(x, y, z), stoneNumber);
				}
			}
		}
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)1, (byte)1, (byte)10), stoneNumber);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)10, (byte)10, (byte)12), stoneNumber);
		
		CuboidHeightMap map = HeightMapHelpers.buildHeightMap(cuboid);
		int airCount = 0;
		int stoneCount = 0;
		for (byte x = 0; x < Structure.CUBOID_EDGE_SIZE; ++x)
		{
			for (byte y = 0; y < Structure.CUBOID_EDGE_SIZE; ++y)
			{
				byte height = map.getHightestSolidBlock(x, y);
				if (CuboidHeightMap.UNKNOWN_HEIGHT == height)
				{
					airCount += 1;
				}
				else
				{
					stoneCount += 1;
				}
			}
		}
		Assert.assertEquals(Structure.CUBOID_EDGE_SIZE * Structure.CUBOID_EDGE_SIZE - 5, airCount);
		Assert.assertEquals(5, stoneCount);
	}
}
