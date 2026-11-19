package com.jeffdisher.october.actions;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.subactions.EntitySubActionJump;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.ContextBuilder;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.BlockFetcher;
import com.jeffdisher.october.utils.CuboidGenerator;


/**
 * While most of the tests in TestCommonChanges indirectly test EntityActionSimpleMove, it is an important enough class
 * with a complex enough implementation to also build a stand-alone test suite for it.
 */
public class TestEntityActionSimpleMove
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	private static Block WATER_SOURCE;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void perf_simpleMove() throws Throwable
	{
		// We want to show the performance of using EntityActionSimpleMove on a complex base cuboid.
		boolean infiniteLoopForProfiler = false;
		boolean longLoopForObjectiveScore = false;
		
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData complexCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		Block dirtBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)31, dirtBlock);
		Block logBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)29, logBlock);
		Block coalOreBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.coal_ore"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)27, coalOreBlock);
		Block ironOreBlock = ENV.blocks.fromItem(ENV.items.getItemById("op.iron_ore"));
		CuboidGenerator.fillPlane(complexCuboid, (byte)25, ironOreBlock);
		Map<CuboidAddress, CuboidData> mappedWorld = Map.of(airCuboid.getCuboidAddress(), airCuboid
			, complexCuboid.getCuboidAddress(), complexCuboid
		);
		
		Function<CuboidAddress, IReadOnlyCuboidData> cuboidLookup = (CuboidAddress address) -> mappedWorld.get(address);
		TickProcessingContext.IBlockFetcher blockFetcher = new BlockFetcher(Map.of(), Set.of(), cuboidLookup);
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(blockFetcher, null, null)
			.finish()
		;
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(5.0f, 5.0f, 0.0f);
		Entity frozen = newEntity.freeze();
		
		if (infiniteLoopForProfiler)
		{
			while(true)
			{
				MutableEntity mutable = MutableEntity.existing(frozen);
				Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, mutable));
			}
		}
		else if (longLoopForObjectiveScore)
		{
			int iterationCount = 1_000_000;
			long startNanos = System.nanoTime();
			for (int i = 0; i < iterationCount; ++i)
			{
				MutableEntity mutable = MutableEntity.existing(frozen);
				Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, mutable));
			}
			long endNanos = System.nanoTime();
			System.out.println("Nanos per: " + ((endNanos - startNanos) / iterationCount));
		}
		else
		{
			Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		}
	}

	@Test
	public void simpleMoveBasics() throws Throwable
	{
		// Test some basic movements with the simple move.
		AbsoluteLocation blockLocation = new AbsoluteLocation(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = blockLocation.getRelative(0, 0, 1).toEntityLocation();
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.0f, 0.0f, EntityActionSimpleMove.Intensity.STANDING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 11.0f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
		
		Assert.assertFalse(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.STANDING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(10.4f, 10.0f, 11.0f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, new EntitySubActionJump<>()).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(10.8f, 10.0f, 11.49f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(4.0f, 0.0f, 4.9f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.0f, 0.0f, EntityActionSimpleMove.Intensity.STANDING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.2f, 10.0f, 11.88f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(4.0f, 0.0f, 3.92f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.6f, 10.0f, 12.17f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(4.0f, 0.0f, 2.94f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.8f, 10.0f, 12.37f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(2.0f, 0.0f, 1.96f), newEntity.newVelocity);
		
		// Our active speed impact is cut in half when in the air so run the last step again.
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.8f, 10.0f, 12.47f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.98f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.0f, 0.0f, EntityActionSimpleMove.Intensity.STANDING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.8f, 10.0f, 12.47f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(0.0f, 0.0f, EntityActionSimpleMove.Intensity.STANDING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(11.8f, 10.0f, 12.37f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), newEntity.newVelocity);
	}

	@Test
	public void simpleMoveEdges() throws Throwable
	{
		// Test the simple move with edge cases.
		AbsoluteLocation blockLocation = new AbsoluteLocation(10, 10, 10);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE.item().number());
		
		TickProcessingContext context = ContextBuilder.build()
			.millisPerTick(100L)
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		
		int entityId = 1;
		MutableEntity newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = blockLocation.getRelative(0, 0, 1).toEntityLocation();
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, new EntitySubActionJump<>()).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(9.6f, 10.0f, 11.49f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(-4.0f, 0.0f, 4.9f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.4f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(9.2f, 10.0f, 11.88f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(-4.0f, 0.0f, 3.92f), newEntity.newVelocity);
		
		// Try again with a different tick rate.
		context = ContextBuilder.build()
			.millisPerTick(10L)
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		
		newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = blockLocation.getRelative(0, 0, 1).toEntityLocation();
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.04f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, new EntitySubActionJump<>()).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(9.96f, 10.0f, 11.06f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(-4.0f, 0.0f, 5.78f), newEntity.newVelocity);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.04f, 0.0f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(9.92f, 10.0f, 11.12f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(-4.0f, 0.0f, 5.68f), newEntity.newVelocity);
		
		// Test an error case we noticed with sign handling when clamping.
		context = ContextBuilder.build()
			.millisPerTick(50L)
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		
		newEntity = MutableEntity.createForTest(entityId);
		newEntity.newLocation = new EntityLocation(20.0f, 20.0f, 20.0f);
		newEntity.newVelocity = new EntityLocation(-1.66f, -1.66f, 0.98f);
		
		Assert.assertTrue(new EntityActionSimpleMove<>(-0.14f, -0.14f, EntityActionSimpleMove.Intensity.WALKING, (byte)0, (byte)0, null).applyChange(context, newEntity));
		Assert.assertEquals(new EntityLocation(19.92f, 19.92f, 20.02f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(-1.66f, -1.66f, 0.49f), newEntity.newVelocity);
	}

	@Test
	public void standInRunningWater()
	{
		// Show that standing in flowing water will push us around.
		EntityLocation oldLocation = new EntityLocation(0.8f, 0.8f, 0.0f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 0), WATER_SOURCE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 0), WATER_SOURCE.item().number());
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, BlockAddress.fromInt(0, 1, 0), (byte)1);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 0, 0), WATER_SOURCE.item().number());
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, BlockAddress.fromInt(1, 0, 0), (byte)1);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(1, 1, 0), WATER_SOURCE.item().number());
		cuboid.setData7(AspectRegistry.BLOCK_DEFINED_BYTE, BlockAddress.fromInt(1, 1, 0), (byte)2);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		EntityActionSimpleMove<IMutablePlayerEntity> action = new EntityActionSimpleMove<>(0.0f
			, 0.0f
			, EntityActionSimpleMove.Intensity.STANDING
			, (byte)5
			, (byte)6
			, null
		);
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(0.85f, 0.85f, 0.0f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
	}

	@Test
	public void topLevelBasicRunning()
	{
		// We just show that the validation works for moving at double normal speed.
		EntityLocation oldLocation = new EntityLocation(0.0f, 0.0f, 1.0f);
		EntityLocation newLocation = new EntityLocation(0.8f, 0.0f, 1.0f);
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = oldLocation;
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityActionSimpleMove<IMutablePlayerEntity> action = new EntityActionSimpleMove<>(0.8f
			, 0.0f
			, EntityActionSimpleMove.Intensity.RUNNING
			, (byte)5
			, (byte)6
			, null
		);
		boolean didApply = action.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		Assert.assertEquals(newLocation, newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
		Assert.assertEquals(EntityActionPeriodic.ENERGY_COST_PER_TICK_RUNNING, newEntity.newEnergyDeficit);
		Assert.assertEquals(5, newEntity.newYaw);
		Assert.assertEquals(6, newEntity.newPitch);
	}

	@Test
	public void moveTowardTarget()
	{
		// A few tests to demonstrate this helper and what it can do.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityLocation start = new EntityLocation(5.0f, 6.0f, 1.0f);
		EntityLocation startingVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation environmentalVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		float startingViscosity = 0.0f;
		EntityLocation targetBase = new EntityLocation(2.0f, 2.0f, 1.0f);
		float walkingBlocksPerSecond = COW.blocksPerSecond();
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity.WALKING;
		long millisPerTick = 100L;
		EntityActionSimpleMove<MutableCreature> move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.06, -0.08, Sub: null", move.toString());
		MutableCreature creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		boolean didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(4.94f, 5.92f, 1.0f), creature.newLocation);
		Assert.assertEquals(102, creature.newYaw);
		
		// Show the impact of the environment.
		environmentalVelocity = new EntityLocation(0.0f, 2.0f, 0.0f);
		targetBase = new EntityLocation(4.5f, 5.0f, 1.0f);
		move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.04, -0.09, Sub: null", move.toString());
		creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(4.96f, 5.91f, 1.0f), creature.newLocation);
		Assert.assertEquals(109, creature.newYaw);
		
		// Show how we handle short distances.
		environmentalVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		targetBase = new EntityLocation(4.98f, 6.05f, 1.0f);
		move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.02, 0.05, Sub: null", move.toString());
		creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(targetBase, creature.newLocation);
		Assert.assertEquals(16, creature.newYaw);
		
		// Show the impact on high environmental velocities on short distances.
		environmentalVelocity = new EntityLocation(-1.0f, 5.0f, 0.0f);
		targetBase = new EntityLocation(4.98f, 6.05f, 1.0f);
		move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by 0.02, -0.10, Sub: null", move.toString());
		// NOTE:  Constructing this specific environmental velocity in the cuboid is difficult so just verify that the action is valid, even without this.
		creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(5.02f, 5.9f, 1.0f), creature.newLocation);
		Assert.assertEquals(16, creature.newYaw);
		
		// Show the impact of viscosity (requires change of cuboid).
		environmentalVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		startingViscosity = ENV.blocks.getViscosityFraction(WATER_SOURCE, false, false);
		targetBase = new EntityLocation(2.0f, 2.0f, 1.0f);
		move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.06, -0.08, Sub: null", move.toString());
		creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		cuboid.setData15(AspectRegistry.BLOCK, start.getBlockLocation().getBlockAddress(), WATER_SOURCE.item().number());
		context = _createSingleCuboidContext(cuboid);
		didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(new EntityLocation(4.97f, 5.96f, 1.0f), creature.newLocation);
		Assert.assertEquals(102, creature.newYaw);
	}

	@Test
	public void dryRunBasics()
	{
		// Show that the behaviour of our "dry run" helper is consistent with actual application.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityLocation groundLocation = new EntityLocation(1.0f, 1.0f, 1.0f);
		EntityLocation airLocation = new EntityLocation(10.0f, 10.0f, 20.0f);
		
		EntityActionSimpleMove<IMutablePlayerEntity> moveTooFar = new EntityActionSimpleMove<>(2.0f
			, 2.0f
			, EntityActionSimpleMove.Intensity.WALKING
			, (byte)5
			, (byte)6
			, null
		);
		
		EntityActionSimpleMove<IMutablePlayerEntity> fallAndMove = new EntityActionSimpleMove<>(0.0f
			, 0.2f
			, EntityActionSimpleMove.Intensity.WALKING
			, (byte)5
			, (byte)6
			, null
		);
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = groundLocation;
		EntityMovementHelpers.HighLevelMovementResult result = moveTooFar.applyChangeForResult(context, newEntity);
		Assert.assertNull(result);
		boolean didApply = moveTooFar.applyChange(context, newEntity);
		Assert.assertFalse(didApply);
		
		newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = airLocation;
		result = fallAndMove.applyChangeForResult(context, newEntity);
		Assert.assertFalse(result.didCollide());
		Assert.assertFalse(result.horizontalCollision());
		didApply = fallAndMove.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		Assert.assertEquals(new EntityLocation(10.0f, 10.1f, 19.9f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, -0.98f), newEntity.newVelocity);
		Assert.assertEquals(EntityActionPeriodic.ENERGY_COST_PER_TICK_WALKING, newEntity.newEnergyDeficit);
		Assert.assertEquals(5, newEntity.newYaw);
		Assert.assertEquals(6, newEntity.newPitch);
	}

	@Test
	public void moveWhileFalling()
	{
		// We want to show that we can still use the "moveTowardTarget" helper while sinking into the target block (within so long as it isn't out of the PathLocation's Z-level).
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityLocation start = new EntityLocation(5.0f, 6.0f, 1.4f);
		EntityLocation startingVelocity = new EntityLocation(0.0f, 0.0f, -0.98f);
		EntityLocation environmentalVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		float startingViscosity = 0.0f;
		EntityLocation targetBase = new EntityLocation(2.0f, 2.0f, 1.0f);
		float walkingBlocksPerSecond = COW.blocksPerSecond();
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity.WALKING;
		long millisPerTick = 100L;
		EntityActionSimpleMove<MutableCreature> move = EntityActionSimpleMove.moveTowardTarget(start
			, startingVelocity
			, environmentalVelocity
			, startingViscosity
			, targetBase
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by -0.06, -0.08, Sub: null", move.toString());
		MutableCreature creature = MutableCreature.existing(CreatureEntity.create(-1, COW, start, 0L));
		creature.newVelocity = startingVelocity;
		boolean didApply = move.applyChange(context, creature);
		Assert.assertTrue(didApply);
		// (note that active movement is 1/2 when not on the ground)
		Assert.assertEquals(new EntityLocation(4.97f, 5.96f, 1.2f), creature.newLocation);
		Assert.assertEquals(102, creature.newYaw);
	}

	@Test
	public void moveInWater()
	{
		// We want to show that "moveTowardTarget" will move more aggressively if walking through water (higher viscosity).
		EntityLocation startAir = new EntityLocation(5.0f, 6.0f, 1.0f);
		EntityLocation startWater = new EntityLocation(16.0f, 17.0f, 1.0f);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, startWater.getBlockLocation().getBlockAddress(), WATER_SOURCE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityLocation startingVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityLocation environmentalVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
		float walkingBlocksPerSecond = COW.blocksPerSecond();
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity.WALKING;
		long millisPerTick = 100L;
		
		EntityActionSimpleMove<MutableCreature> moveAir = EntityActionSimpleMove.moveTowardTarget(startAir
			, startingVelocity
			, environmentalVelocity
			, 0.0f
			, startAir.getRelative(0.0f, 0.08f, 0.0f)
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by 0.00, 0.08, Sub: null", moveAir.toString());
		MutableCreature creature = MutableCreature.existing(CreatureEntity.create(-1, COW, startAir, 0L));
		creature.newVelocity = startingVelocity;
		boolean didApply = moveAir.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(startAir.getRelative(0.0f, 0.08f, 0.0f), creature.newLocation);
		
		EntityActionSimpleMove<MutableCreature> moveWater = EntityActionSimpleMove.moveTowardTarget(startWater
			, startingVelocity
			, environmentalVelocity
			, 0.5f
			, startWater.getRelative(0.0f, 0.08f, 0.0f)
			, walkingBlocksPerSecond
			, intensity
			, millisPerTick
		);
		Assert.assertEquals("SimpleMove(WALKING), by 0.00, 0.10, Sub: null", moveWater.toString());
		creature = MutableCreature.existing(CreatureEntity.create(-1, COW, startWater, 0L));
		creature.newVelocity = startingVelocity;
		didApply = moveWater.applyChange(context, creature);
		Assert.assertTrue(didApply);
		Assert.assertEquals(startWater.getRelative(0.0f, 0.05f, 0.0f), creature.newLocation);
	}

	@Test
	public void dryRunCollision()
	{
		// Show what happens in a dry run which triggers a horizontal collision.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidGenerator.fillPlane(cuboid, (byte)0, STONE);
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 0, 1), STONE.item().number());
		cuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(0, 1, 1), STONE.item().number());
		TickProcessingContext context = _createSingleCuboidContext(cuboid);
		
		EntityLocation groundLocation = new EntityLocation(1.0f, 1.0f, 1.0f);
		
		EntityActionSimpleMove<IMutablePlayerEntity> move = new EntityActionSimpleMove<>(-0.1f
			, -0.1f
			, EntityActionSimpleMove.Intensity.WALKING
			, (byte)5
			, (byte)6
			, null
		);
		
		MutableEntity newEntity = MutableEntity.createForTest(1);
		newEntity.newLocation = groundLocation;
		EntityMovementHelpers.HighLevelMovementResult result = move.applyChangeForResult(context, newEntity);
		Assert.assertTrue(result.didCollide());
		Assert.assertTrue(result.horizontalCollision());
		boolean didApply = move.applyChange(context, newEntity);
		Assert.assertTrue(didApply);
		
		// Even though we collided, applying this should see the creature slide along the wall.
		Assert.assertEquals(new EntityLocation(1.0f, 0.9f, 1.0f), newEntity.newLocation);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), newEntity.newVelocity);
		Assert.assertEquals(EntityActionPeriodic.ENERGY_COST_PER_TICK_WALKING, newEntity.newEnergyDeficit);
		Assert.assertEquals(5, newEntity.newYaw);
		Assert.assertEquals(6, newEntity.newPitch);
	}


	private static TickProcessingContext _createSingleCuboidContext(CuboidData cuboid)
	{
		TickProcessingContext context = ContextBuilder.build()
			.lookups(ContextBuilder.buildSingleFetcher(cuboid), null, null)
			.finish()
		;
		return context;
	}
}
