package com.jeffdisher.october.logic;

import java.util.List;
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
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestHeightMapHelpers
{
	private static Environment ENV;
	private static Block STONE;
	private static Block DIRT;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		DIRT = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void buildHeightMap() throws Throwable
	{
		// Populate a cuboid with some values and observe the expected height map.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		short stoneNumber = STONE.item().number();
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
		byte[][] yMajorUnsafe = map.getUnsafeAccess();
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			byte[] unsafeRow = yMajorUnsafe[y];
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
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
				byte unsafeHeight = (null != unsafeRow)
					? unsafeRow[x]
					: CuboidHeightMap.UNKNOWN_HEIGHT
				;
				Assert.assertEquals(height, unsafeHeight);
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
		short stoneNumber = STONE.item().number();
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 10), stoneNumber);
		bottom.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 10), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(10, 10, 12), stoneNumber);
		top.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 2), stoneNumber);
		
		CuboidHeightMap bottomMap = HeightMapHelpers.buildHeightMap(bottom);
		CuboidHeightMap topMap = HeightMapHelpers.buildHeightMap(top);
		ColumnHeightMap column = ColumnHeightMap.build()
			.consume(topMap, high)
			.consume(bottomMap, low)
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
		short stoneNumber = STONE.item().number();
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
		short stoneNumber = STONE.item().number();
		CuboidData x0z0 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData x0z1 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), ENV.special.AIR);
		CuboidData x1z0 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 0), ENV.special.AIR);
		CuboidData x1z1 = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(1, 0, 1), ENV.special.AIR);
		
		// Seed some initial data.
		x0z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), stoneNumber);
		x0z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 2, 1), stoneNumber);
		x1z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 3, 1), stoneNumber);
		x1z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 4, 1), stoneNumber);
		
		ColumnHeightMap x0map = HeightMapHelpers.buildSingleColumn(Map.of(x0z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z0)
			, x0z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z1)
		));
		ColumnHeightMap x1map = HeightMapHelpers.buildSingleColumn(Map.of(x1z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z0)
			, x1z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z1)
		));
		Assert.assertEquals(1, x0map.getHeight(1, 1));
		Assert.assertEquals(33, x0map.getHeight(1, 2));
		Assert.assertEquals(1, x1map.getHeight(1, 3));
		Assert.assertEquals(33, x1map.getHeight(1, 4));
		
		// Make a few small changes and rebuild the maps - overlap under and over existing height.
		x0z0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 2, 1), stoneNumber);
		x1z1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 3, 1), stoneNumber);
		x0map = HeightMapHelpers.buildSingleColumn(Map.of(x0z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z0)
			, x0z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x0z1)
		));
		x1map = HeightMapHelpers.buildSingleColumn(Map.of(x1z0.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z0)
			, x1z1.getCuboidAddress(), HeightMapHelpers.buildHeightMap(x1z1)
		));
		Assert.assertEquals(1, x0map.getHeight(1, 1));
		Assert.assertEquals(33, x0map.getHeight(1, 2));
		Assert.assertEquals(33, x1map.getHeight(1, 3));
		Assert.assertEquals(33, x1map.getHeight(1, 4));
	}

	@Test
	public void assertSortOrder() throws Throwable
	{
		// Show that we now fail an assertion if we try to use the ColumnHeightMap.Builder in the wrong sorted order.
		CuboidAddress low = CuboidAddress.fromInt(0, 0, -1);
		CuboidAddress high = CuboidAddress.fromInt(0, 0, 1);
		CuboidData bottom = CuboidGenerator.createFilledCuboid(low, ENV.special.AIR);
		CuboidData top = CuboidGenerator.createFilledCuboid(high, ENV.special.AIR);
		
		CuboidHeightMap bottomMap = HeightMapHelpers.buildHeightMap(bottom);
		CuboidHeightMap topMap = HeightMapHelpers.buildHeightMap(top);
		
		try
		{
			ColumnHeightMap.build()
				.consume(topMap, high)
				.consume(bottomMap, low)
				.freeze()
			;
			Assert.fail();
		}
		catch (AssertionError e)
		{
			// Expected.
		}
	}

	@Test
	public void buildSingleColumn() throws Throwable
	{
		// Populate a cuboid with some values and observe the expected height map.
		CuboidAddress low = CuboidAddress.fromInt(0, 0, -1);
		CuboidAddress mid = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress high = CuboidAddress.fromInt(0, 0, 1);
		
		CuboidData bottom = CuboidGenerator.createFilledCuboid(low, ENV.special.AIR);
		CuboidData centre = CuboidGenerator.createFilledCuboid(mid, STONE);
		CuboidData top = CuboidGenerator.createFilledCuboid(high, ENV.special.AIR);
		
		CuboidHeightMap bottomMap = HeightMapHelpers.buildHeightMap(bottom);
		CuboidHeightMap centreMap = HeightMapHelpers.buildHeightMap(centre);
		CuboidHeightMap topMap = HeightMapHelpers.buildHeightMap(top);
		
		ColumnHeightMap columnMap = HeightMapHelpers.buildSingleColumn(Map.of(low, bottomMap
			, mid, centreMap
			, high, topMap
		));
		for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
		{
			for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
			{
				int height = columnMap.getHeight(x, y);
				Assert.assertEquals(31, height);
			}
		}
	}

	@Test
	public void updateHeightMap() throws Throwable
	{
		// Build a height map with some interesting features and show how a complex update works.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		short airNumber = ENV.special.AIR.item().number();
		short stoneNumber = STONE.item().number();
		short dirtNumber = DIRT.item().number();
		BlockAddress ba1_1_10 = BlockAddress.fromInt(1, 1, 10);
		BlockAddress ba1_1_1 = BlockAddress.fromInt(1, 1, 1);
		BlockAddress ba2_2_3 = BlockAddress.fromInt(2, 2, 3);
		BlockAddress ba10_10_12 = BlockAddress.fromInt(10, 10, 12);
		BlockAddress ba10_10_17 = BlockAddress.fromInt(10, 10, 17);
		BlockAddress ba20_20_0 = BlockAddress.fromInt(20, 20, 0);
		cuboid.setData15(AspectRegistry.BLOCK, ba1_1_10, stoneNumber);
		cuboid.setData15(AspectRegistry.BLOCK, ba2_2_3, stoneNumber);
		cuboid.setData15(AspectRegistry.BLOCK, ba10_10_12, stoneNumber);
		cuboid.setData15(AspectRegistry.BLOCK, ba10_10_17, stoneNumber);
		
		CuboidHeightMap map = HeightMapHelpers.buildHeightMap(cuboid);
		
		List<BlockAddress> blocksChangedToAir = List.of(ba1_1_10, ba2_2_3, ba10_10_12);
		for (BlockAddress address : blocksChangedToAir)
		{
			cuboid.setData15(AspectRegistry.BLOCK, address, airNumber);
		}
		List<BlockAddress> blocksChangedToNotAir = List.of(ba1_1_1, ba10_10_17, ba20_20_0);
		for (BlockAddress address : blocksChangedToNotAir)
		{
			cuboid.setData15(AspectRegistry.BLOCK, address, dirtNumber);
		}
		map = HeightMapHelpers.updateHeightMap(map, cuboid, blocksChangedToAir, blocksChangedToNotAir);
		
		Assert.assertEquals(ba1_1_1.z(), map.getHightestSolidBlock(ba1_1_1.x(), ba1_1_1.y()));
		Assert.assertEquals(CuboidHeightMap.UNKNOWN_HEIGHT, map.getHightestSolidBlock(ba2_2_3.x(), ba2_2_3.y()));
		Assert.assertEquals(ba10_10_17.z(), map.getHightestSolidBlock(ba10_10_17.x(), ba10_10_17.y()));
		Assert.assertEquals(ba20_20_0.z(), map.getHightestSolidBlock(ba20_20_0.x(), ba20_20_0.y()));
		Assert.assertEquals(CuboidHeightMap.UNKNOWN_HEIGHT, map.getHightestSolidBlock(30, 30));
	}

	@Test
	public void perf_SingleColumn() throws Throwable
	{
		// We want to show the performance of merging multiple sparse cuboids in a single column.
		boolean longLoopForObjectiveScore = false;
		
		CuboidAddress c0 = CuboidAddress.fromInt(0, 0, 0);
		CuboidAddress c1 = CuboidAddress.fromInt(0, 0, 1);
		CuboidAddress c2 = CuboidAddress.fromInt(0, 0, 2);
		CuboidAddress c3 = CuboidAddress.fromInt(0, 0, 3);
		CuboidAddress c4 = CuboidAddress.fromInt(0, 0, 4);
		CuboidAddress c5 = CuboidAddress.fromInt(0, 0, 5);
		
		short stoneNumber = STONE.item().number();
		CuboidData d0 = CuboidGenerator.createFilledCuboid(c0, ENV.special.AIR);
		d0.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 1), stoneNumber);
		CuboidData d1 = CuboidGenerator.createFilledCuboid(c1, ENV.special.AIR);
		d1.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(2, 2, 2), stoneNumber);
		CuboidData d2 = CuboidGenerator.createFilledCuboid(c2, ENV.special.AIR);
		d2.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(3, 3, 3), stoneNumber);
		CuboidData d3 = CuboidGenerator.createFilledCuboid(c3, ENV.special.AIR);
		d3.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(4, 4, 4), stoneNumber);
		CuboidData d4 = CuboidGenerator.createFilledCuboid(c4, ENV.special.AIR);
		d4.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 5), stoneNumber);
		CuboidData d5 = CuboidGenerator.createFilledCuboid(c5, ENV.special.AIR);
		d5.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(6, 6, 6), stoneNumber);
		
		CuboidHeightMap h0 = HeightMapHelpers.buildHeightMap(d0);
		CuboidHeightMap h1 = HeightMapHelpers.buildHeightMap(d1);
		CuboidHeightMap h2 = HeightMapHelpers.buildHeightMap(d2);
		CuboidHeightMap h3 = HeightMapHelpers.buildHeightMap(d3);
		CuboidHeightMap h4 = HeightMapHelpers.buildHeightMap(d4);
		CuboidHeightMap h5 = HeightMapHelpers.buildHeightMap(d5);
		
		Map<CuboidAddress, CuboidHeightMap> cuboidHeightMaps = Map.of(c0, h0
			, c1, h1
			, c2, h2
			, c3, h3
			, c4, h4
			, c5, h5
		);
		
		if (longLoopForObjectiveScore)
		{
			int iterationCount = 1_000_000;
			long startNanos = System.nanoTime();
			for (int i = 0; i < iterationCount; ++i)
			{
				HeightMapHelpers.buildSingleColumn(cuboidHeightMaps);
			}
			long endNanos = System.nanoTime();
			System.out.println("Nanos per: " + ((endNanos - startNanos) / iterationCount));
		}
		else
		{
			HeightMapHelpers.buildSingleColumn(cuboidHeightMaps);
		}
	}
}
