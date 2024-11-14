package com.jeffdisher.october.logic;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		BlockAddress blockAddress = BlockAddress.fromInt(5, 6, 7);
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
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
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 10), stoneNumber);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 12), stoneNumber);
		
		CuboidHeightMap map = HeightMapHelpers.buildHeightMap(cuboid);
		int airCount = 0;
		int stoneCount = 0;
		for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
		{
			for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
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
		Assert.assertEquals(Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE - 5, airCount);
		Assert.assertEquals(5, stoneCount);
	}

	@Test
	public void mergeToOneColumnMap() throws Throwable
	{
		// Populate a cuboid with some values and observe the expected height map.
		CuboidAddress low = CuboidAddress.fromInt(0, 0, -1);
		CuboidAddress high = CuboidAddress.fromInt(0, 0, 1);
		int lowZ = low.getBase().z();
		int highZ = high.getBase().z();
		CuboidData bottom = CuboidGenerator.createFilledCuboid(low, ENV.special.AIR);
		CuboidData top = CuboidGenerator.createFilledCuboid(high, ENV.special.AIR);
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 10), stoneNumber);
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 10), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 12), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 2), stoneNumber);
		
		CuboidHeightMap bottomMap = HeightMapHelpers.buildHeightMap(bottom);
		CuboidHeightMap topMap = HeightMapHelpers.buildHeightMap(top);
		ColumnHeightMap column = ColumnHeightMap.build()
				.consume(bottomMap, low)
				.consume(topMap, high)
				.freeze()
		;
		Assert.assertEquals(Integer.MIN_VALUE, column.getHeight(3, 3));
		Assert.assertEquals(lowZ + 10, column.getHeight(1, 1));
		Assert.assertEquals(highZ + 2, column.getHeight(2, 2));
		Assert.assertEquals(highZ + 12, column.getHeight(10, 10));
	}

	@Test
	public void mergeColumnCollection() throws Throwable
	{
		// Populate a cuboid with some values and observe the expected height map.
		CuboidAddress low = CuboidAddress.fromInt(0, 0, -1);
		CuboidAddress high = CuboidAddress.fromInt(0, 0, 1);
		CuboidAddress distinct = CuboidAddress.fromInt(-1, -1, -2);
		int lowZ = low.getBase().z();
		int highZ = high.getBase().z();
		int distinctZ = distinct.getBase().z();
		CuboidData bottom = CuboidGenerator.createFilledCuboid(low, ENV.special.AIR);
		CuboidData top = CuboidGenerator.createFilledCuboid(high, ENV.special.AIR);
		CuboidData other = CuboidGenerator.createFilledCuboid(distinct, ENV.special.AIR);
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 10), stoneNumber);
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 10), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 12), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 2), stoneNumber);
		other.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), stoneNumber);
		
		Map<CuboidAddress, CuboidHeightMap> perCuboid = Map.of(low, HeightMapHelpers.buildHeightMap(bottom)
				, high, HeightMapHelpers.buildHeightMap(top)
				, distinct, HeightMapHelpers.buildHeightMap(other)
		);
		Map<CuboidColumnAddress, ColumnHeightMap> columns = HeightMapHelpers.buildColumnMaps(perCuboid);
		
		Assert.assertEquals(low.getColumn(), high.getColumn());
		Assert.assertEquals(2, columns.size());
		
		ColumnHeightMap column = columns.get(low.getColumn());
		Assert.assertEquals(Integer.MIN_VALUE, column.getHeight(3, 3));
		Assert.assertEquals(lowZ + 10, column.getHeight(1, 1));
		Assert.assertEquals(highZ + 2, column.getHeight(2, 2));
		Assert.assertEquals(highZ + 12, column.getHeight(10, 10));
		
		ColumnHeightMap isolated = columns.get(distinct.getColumn());
		Assert.assertEquals(Integer.MIN_VALUE, isolated.getHeight(3, 3));
		Assert.assertEquals(distinctZ + 1, isolated.getHeight(1, 1));
	}

	@Test
	public void rebuildHeightMap() throws Throwable
	{
		// Use the height map rebuild helper to handle changes across a few columns.
		short stoneNumber = ENV.items.getItemById("op.stone").number();
		CuboidData x0z0 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData x0z1 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR);
		CuboidData x1z0 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 0), ENV.special.AIR);
		CuboidData x1z1 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 1), ENV.special.AIR);
		
		// Seed some initial data.
		x0z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), stoneNumber);
		x0z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 2, 1), stoneNumber);
		x1z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 3, 1), stoneNumber);
		x1z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 4, 1), stoneNumber);
		
		Map<CuboidAddress, CuboidHeightMap> initialCuboids = Map.of(
				x0z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z0)
				, x0z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z1)
				, x1z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z0)
				, x1z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z1)
		);
		Map<CuboidColumnAddress, ColumnHeightMap> first = HeightMapHelpers.rebuildColumnMaps(Map.of()
				, initialCuboids
				, Map.of()
				, initialCuboids.keySet()
		);
		Assert.assertEquals(2, first.size());
		Assert.assertEquals(1, first.get(x0z0.getCuboidAddress().getColumn()).getHeight(1, 1));
		Assert.assertEquals(33, first.get(x0z0.getCuboidAddress().getColumn()).getHeight(1, 2));
		Assert.assertEquals(1, first.get(x1z0.getCuboidAddress().getColumn()).getHeight(1, 3));
		Assert.assertEquals(33, first.get(x1z0.getCuboidAddress().getColumn()).getHeight(1, 4));
		
		// Make a few small changes and rebuild the maps - overlap under and over existing height.
		x0z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 2, 1), stoneNumber);
		x1z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 3, 1), stoneNumber);
		Map<CuboidAddress, CuboidHeightMap> changedCuboids = Map.of(
				x0z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z0)
				, x1z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z1)
		);
		Map<CuboidColumnAddress, ColumnHeightMap> second = HeightMapHelpers.rebuildColumnMaps(first
				, initialCuboids
				, changedCuboids
				, initialCuboids.keySet()
		);
		Assert.assertEquals(2, second.size());
		Assert.assertEquals(1, second.get(x0z0.getCuboidAddress().getColumn()).getHeight(1, 1));
		Assert.assertEquals(33, second.get(x0z0.getCuboidAddress().getColumn()).getHeight(1, 2));
		Assert.assertEquals(33, second.get(x1z0.getCuboidAddress().getColumn()).getHeight(1, 3));
		Assert.assertEquals(33, second.get(x1z0.getCuboidAddress().getColumn()).getHeight(1, 4));
		
		// Unload 1 column.
		Map<CuboidAddress, CuboidHeightMap> remainingCuboids = Map.of(
				x0z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z0)
				, x0z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z1)
		);
		Map<CuboidColumnAddress, ColumnHeightMap> third = HeightMapHelpers.rebuildColumnMaps(second
				, remainingCuboids
				, Map.of()
				, remainingCuboids.keySet()
		);
		Assert.assertEquals(1, third.size());
		Assert.assertTrue(second.get(x0z0.getCuboidAddress().getColumn()) == third.get(x0z0.getCuboidAddress().getColumn()));
	}
}
