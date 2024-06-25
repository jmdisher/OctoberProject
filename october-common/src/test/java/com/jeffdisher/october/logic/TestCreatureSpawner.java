package com.jeffdisher.october.logic;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.TickProcessingContext;
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
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, completedCuboids
				, Map.of()
		);
		
		Assert.assertEquals(2.0f, entity.location().z(), 0.01f);
	}

	@Test
	public void singleCuboidPeaceful()
	{
		// Create a cuboid of air with a single stone block located such that our random generator will find it but will fail to spawn due to peaceful mode.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)1), STONE.item().number());
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		int randomValue = 5;
		TickProcessingContext context = new TickProcessingContext(CreatureProcessor.MINIMUM_TICKS_TO_NEW_ACTION + 1L
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
				, Difficulty.PEACEFUL
		);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, completedCuboids
				, Map.of()
		);
		
		Assert.assertNull(entity);
	}

	@Test
	public void stackedCuboids()
	{
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = _buildTestWorld();
		TickProcessingContext context = _createContext(completedCuboids, 1);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, completedCuboids
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
		Map<CuboidAddress, IReadOnlyCuboidData> completedCuboids = Map.of(cuboid.getCuboidAddress(), cuboid);
		TickProcessingContext context = _createContext(completedCuboids, 5);
		CreatureEntity entity = CreatureSpawner.trySpawnCreature(context
				, completedCuboids
				, Map.of()
		);
		Assert.assertNull(entity);
	}


	private static TickProcessingContext _createContext(Map<CuboidAddress, IReadOnlyCuboidData> world, int randomValue)
	{
		TickProcessingContext context = new TickProcessingContext(CreatureProcessor.MINIMUM_TICKS_TO_NEW_ACTION + 1L
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
				, Difficulty.HOSTILE
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
