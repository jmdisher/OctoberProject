package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreatureSpawner
{
	private static Environment ENV;
	private static Block AIR;
	private static Block STONE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		AIR = ENV.blocks.fromItem(ENV.items.getItemById("op.air"));
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void singleCuboid()
	{
		// Create a cuboid of air with a single stone block located such that our random generator will find it and show that a single orc is spawned on the block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		
		Assert.assertEquals(2.0f, entity.location().z(), 0.01f);
	}

	@Test
	public void sunnyCuboid()
	{
		// This is the same as singleCuboid() but it is bright out so the spawn should fail.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createSunnyContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		
		Assert.assertNull(entity);
	}

	@Test
	public void singleCuboidPeaceful()
	{
		// Create a cuboid of air with a single stone block located such that our random generator will find it but will fail to spawn due to peaceful mode.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		int randomValue = 5;
		WorldConfig config = new WorldConfig();
		config.difficulty = Difficulty.PEACEFUL;
		TickProcessingContext context = ContextBuilder.build()
				.lookups((AbsoluteLocation location) -> {
						IReadOnlyCuboidData oneCuboid = completedCuboids.get(location.getCuboidAddress());
						return (null != oneCuboid)
								? new BlockProxy(location.getBlockAddress(), oneCuboid)
								: null
						;
					}, null)
				.boundedRandom(randomValue)
				.config(config)
				.finish()
		;
		
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		
		Assert.assertNull(entity);
	}

	@Test
	public void stackedCuboids()
	{
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = _buildTestWorld();
		Map<CuboidAddress, CuboidHeightMap> cuboidMaps = completedCuboids.entrySet().stream().collect(
				Collectors.toMap((Map.Entry<CuboidAddress, IReadOnlyCuboidData> entry) -> entry.getKey(), (Map.Entry<CuboidAddress, IReadOnlyCuboidData> entry) -> HeightMapHelpers.buildHeightMap(entry.getValue()))
		);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(cuboidMaps);
		TickProcessingContext context = _createContext(completedCuboids, 1);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		
		// Note that this will fail half the time if it selects the stone cuboid (hash order is non-deterministic).
		if (null != entity)
		{
			Assert.assertEquals(0.0f, entity.location().z(), 0.01f);
		}
	}

	@Test
	public void singleCuboidLit()
	{
		// Use the same approach as singleCuboid, so we know where the spawn should happen, but set the LIGHT aspect so that the spawn will fail.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, BlockAddress.fromInt(5, 5, 2), (byte)1);
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		Assert.assertNull(entity);
	}

	@Test
	public void singleCuboidNearPlayer()
	{
		// Use the same approach as singleCuboid, so we know where the spawn should happen, but put a player near them so that the spawn will fail.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(1, MutableEntity.createForTest(1).freeze()), Map.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		Assert.assertNull(entity);
	}

	@Test
	public void emptyHeightMap()
	{
		// Create a situation where spawning should be ideal but will fail because of an empty height map.
		// This sometimes happens if a cuboid just loaded before the tick and its map hasn't been generated/merged before snapshot.
		// Create a cuboid of air with a single stone block located such that our random generator will find it and show that a single orc is spawned on the block.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 1), STONE.item().number());
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		ColumnHeightMap columnMap = ColumnHeightMap.build().freeze();
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Map.of(), Map.of())
				, completedCuboids
				, Map.of(cuboid.getCuboidAddress().getColumn(), columnMap)
				, Map.of()
		);
		Assert.assertNull(entity);
	}


	private static TickProcessingContext _createSunnyContext(Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		// 1 quarter through the day is noon.
		WorldConfig defaults = new WorldConfig();
		long startTick = defaults.ticksPerDay * 1L / 4L;
		Assert.assertEquals((byte)15, PropagationHelpers.currentSkyLightValue(startTick, defaults.ticksPerDay, defaults.dayStartTick));
		return _createContextWithTick(startTick, world, randomValue);
	}

	private static TickProcessingContext _createContext(Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		// By default, we will use darkest time of day.
		WorldConfig defaults = new WorldConfig();
		long startTick = defaults.ticksPerDay * 3L / 4L;
		Assert.assertEquals((byte)0, PropagationHelpers.currentSkyLightValue(startTick, defaults.ticksPerDay, defaults.dayStartTick));
		return _createContextWithTick(startTick, world, randomValue);
	}

	private static TickProcessingContext _createContextWithTick(long gameTick, Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		TickProcessingContext context = ContextBuilder.build()
				.tick(gameTick)
				.lookups((AbsoluteLocation location) -> {
						IReadOnlyCuboidData cuboid = world.get(location.getCuboidAddress());
						return (null != cuboid)
								? new BlockProxy(location.getBlockAddress(), cuboid)
								: null
						;
					}, null)
				.assigner(new CreatureIdAssigner())
				.boundedRandom(randomValue)
				.finish()
		;
		return context;
	}

	private static Map<CuboidAddress, IReadOnlyCuboidData> _buildTestWorld()
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		return Map.of(airCuboid.getCuboidAddress(), airCuboid
				, stoneCuboid.getCuboidAddress(), stoneCuboid
		);
	}
}
