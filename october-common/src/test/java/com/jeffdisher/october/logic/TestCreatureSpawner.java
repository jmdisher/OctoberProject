package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.Set;
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
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.worldgen.CuboidGenerator;


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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Set.of(), Set.of())
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createSunnyContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Set.of(), Set.of())
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		int randomValue = 5;
		WorldConfig config = new WorldConfig();
		config.difficulty = Difficulty.PEACEFUL;
		TickProcessingContext context = new TickProcessingContext(1L
				, (AbsoluteLocation location) -> {
					IReadOnlyCuboidData oneCuboid = completedCuboids.get(location.getCuboidAddress());
					return (null != oneCuboid)
							? new BlockProxy(location.getBlockAddress(), oneCuboid)
							: null
					;
				}
				, null
				, null
				, null
				, null
				, (int bound) -> (bound > randomValue)
					? randomValue
					: (bound - 1)
				, config
				, 100L
		);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Set.of(), Set.of())
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
				, new EntityCollection(Set.of(), Set.of())
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		cuboid.setData7(AspectRegistry.LIGHT, new BlockAddress((byte)5, (byte)5, (byte)2), (byte)1);
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Set.of(), Set.of())
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		CuboidHeightMap heightMap = HeightMapHelpers.buildHeightMap(cuboid);
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		Map<CuboidColumnAddress, ColumnHeightMap> completedHeightMaps = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), heightMap));
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, new EntityCollection(Set.of(MutableEntity.createForTest(1).freeze()), Set.of())
				, completedCuboids
				, completedHeightMaps
				, Map.of()
		);
		Assert.assertNull(entity);
	}


	private static TickProcessingContext _createSunnyContext(Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		// We will pick tick 1000 since it will be bright.
		return _createContextWithTick(1000L, world, randomValue);
	}

	private static TickProcessingContext _createContext(Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		// By default, we will use darkest time of day.
		return _createContextWithTick(500L, world, randomValue);
	}

	private static TickProcessingContext _createContextWithTick(long gameTick, Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		TickProcessingContext context = new TickProcessingContext(gameTick
				, (AbsoluteLocation location) -> {
					IReadOnlyCuboidData cuboid = world.get(location.getCuboidAddress());
					return (null != cuboid)
							? new BlockProxy(location.getBlockAddress(), cuboid)
							: null
					;
				}
				, null
				, null
				, null
				, new CreatureIdAssigner()
				, (int bound) -> (bound > randomValue)
					? randomValue
					: (bound - 1)
				, new WorldConfig()
				, 100L
		);
		return context;
	}

	private static Map<CuboidAddress, IReadOnlyCuboidData> _buildTestWorld()
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)-1), STONE);
		return Map.of(airCuboid.getCuboidAddress(), airCuboid
				, stoneCuboid.getCuboidAddress(), stoneCuboid
		);
	}
}
