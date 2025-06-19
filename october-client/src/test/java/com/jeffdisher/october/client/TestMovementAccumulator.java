package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMovementAccumulator
{
	public static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Item STONE_ITEM;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		STONE = ENV.blocks.fromItem(STONE_ITEM);
		WATER_SOURCE = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void orientationStandingOnly() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Set our orientation and stand around until the action is generated.
		byte yaw = 5;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		currentTimeMillis += 50L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation(currentTimeMillis);
	}

	@Test
	public void basicWalking() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Walk until the action is generated.
		currentTimeMillis += 50L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.2f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.44f, 0.0f), listener.thisEntity.location());
		// NOTE:  This z-vector should be 0.0 but the step is so small it can't realize a collision with the ground.
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, -0.1f), listener.thisEntity.velocity());
	}

	@Test
	public void jumpAndStand() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Jump, then stand until the action is generated, and again to see that the jump impacts the second action.
		boolean didJump = accumulator.enqueueSubAction(new EntityChangeJump<>());
		Assert.assertTrue(didJump);
		currentTimeMillis += 60L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.stand(currentTimeMillis);
		// Note that, even though this should tick-over and generate an event, we didn't do anything so it will be a
		// null event.  Note that completing the event will also mean the jump is "on deck" so will force generation of
		// the next tick's event.
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.11f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 5.68f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.5f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.9f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.55f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.8f), listener.thisEntity.velocity());
	}

	@Test
	public void jumpAndWalk() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Jump, then stand until the action is generated, and again to see that the jump impacts the second action.
		boolean didJump = accumulator.enqueueSubAction(new EntityChangeJump<>());
		Assert.assertTrue(didJump);
		currentTimeMillis += 60L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.24f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.48f, 0.11f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 5.68f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.8f, 0.5f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 4.9f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.84f, 0.55f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 4.8f), listener.thisEntity.velocity());
	}

	@Test
	public void fallingStand() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData topCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData bottomCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(topCuboid, HeightMapHelpers.buildHeightMap(topCuboid));
		accumulator.setCuboid(bottomCuboid, HeightMapHelpers.buildHeightMap(bottomCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.04f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.59f), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(topCuboid, bottomCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.08f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.18f), listener.thisEntity.velocity());
	}

	@Test
	public void fallingThroughWater() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData topCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData bottomCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), WATER_SOURCE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(16.0f, 16.0f, 2.0f);
		mutable.newVelocity = new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(topCuboid, HeightMapHelpers.buildHeightMap(topCuboid));
		accumulator.setCuboid(bottomCuboid, HeightMapHelpers.buildHeightMap(bottomCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, 2.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -0.4f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(topCuboid, bottomCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -2.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -2.4f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -20.0f), listener.thisEntity.velocity());
	}

	@Test
	public void airFallRates() throws Throwable
	{
		// We want to show that entities will still end up in the same place when falling with different increments (tests rounding errors).
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(16.0f, 16.0f, 20.0f);
		Entity entity = mutable.freeze();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		
		Entity smallTimes = _runFallingTest(7L, 17, cuboid, entity);
		Entity largeTimes = _runFallingTest(17L, 7, cuboid, entity);
		Assert.assertEquals(19.93f, smallTimes.location().z(), 0.01f);
		Assert.assertEquals(19.93f, largeTimes.location().z(), 0.01f);
		Assert.assertEquals(-1.17f, smallTimes.velocity().z(), 0.01f);
		Assert.assertEquals(-1.17f, largeTimes.velocity().z(), 0.01f);
	}

	@Test
	public void waterFallRates() throws Throwable
	{
		// We want to show that entities will still end up in the same place when falling with different increments (tests rounding errors).
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(16.0f, 16.0f, 20.0f);
		Entity entity = mutable.freeze();
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), WATER_SOURCE);
		
		// TODO:  Fix this positioning disagreement (20.0 vs 19.96)!
		Entity smallTimes = _runFallingTest(7L, 17, cuboid, entity);
		Assert.assertEquals(20.0f, smallTimes.location().z(), 0.01f);
		Assert.assertEquals(-0.57f, smallTimes.velocity().z(), 0.01f);
		Entity largeTimes = _runFallingTest(17L, 7, cuboid, entity);
		Assert.assertEquals(19.96f, largeTimes.location().z(), 0.01f);
		Assert.assertEquals(-0.57f, largeTimes.velocity().z(), 0.01f);
	}

	@Test
	public void swim() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), WATER_SOURCE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(15.0f, 15.0f, 15.0f);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(cuboid, HeightMapHelpers.buildHeightMap(cuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Swim, then move forward until the action is generated, and again to see that the swim impacts the second action.
		boolean didSwim = accumulator.enqueueSubAction(new EntityChangeSwim<>());
		Assert.assertTrue(didSwim);
		currentTimeMillis += 60L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(15.0f, 15.12f, 14.98f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, -0.29f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(15.0f, 15.24f, 15.06f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.8f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(15.0f, 15.4f, 15.41f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.41f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(15.0f, 15.42f, 15.45f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.36f), listener.thisEntity.velocity());
		
		// We now want to coast for a bit and see how they move.
		currentTimeMillis += 50L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(15.0f, 15.52f, 15.66f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.12f), listener.thisEntity.velocity());
		
		currentTimeMillis += 50L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(15.0f, 15.6f, 15.82f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 3.92f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(15.0f, 15.61f, 15.86f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 1.0f, 3.87f), listener.thisEntity.velocity());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData blockingCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 1), STONE);
		CuboidHeightMap cuboidMap = HeightMapHelpers.buildHeightMap(cuboid);
		CuboidHeightMap blockingMap = HeightMapHelpers.buildHeightMap(blockingCuboid);
		ColumnHeightMap columnMap = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), cuboidMap
			, blockingCuboid.getCuboidAddress(), blockingMap
		)).values().iterator().next();
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(16.0f, 16.0f, 16.0f);
		mutable.newInventory.addAllItems(STONE_ITEM, 1);
		mutable.setSelectedKey(1);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(cuboid, cuboidMap);
		accumulator.setCuboid(blockingCuboid, blockingMap);
		listener.thisEntityDidLoad(entity);
		listener.cuboidDidLoad(cuboid, cuboidMap, columnMap);
		listener.cuboidDidLoad(blockingCuboid, blockingMap, columnMap);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Place a block and verify that the output information is correct (we need to skip past the first tick, first).
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		accumulator.enqueueSubAction(new MutationPlaceSelectedBlock(new AbsoluteLocation(15, 15, 15), new AbsoluteLocation(15, 16, 15)));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid, blockingCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		
		// We should now verify that the local accumulation't output shows the inventory empty, the block placed, and the height map correct.
		Assert.assertEquals(0, listener.thisEntity.inventory().currentEncumbrance);
		Assert.assertEquals(STONE.item().number(), listener.loadedCuboids.get(cuboid.getCuboidAddress()).getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(15, 15, 15)));
		Assert.assertEquals(63, listener.heightMaps.get(cuboid.getCuboidAddress().getColumn()).getHeight(15, 15));
		Assert.assertEquals(1, listener.cuboidChangeCount);
	}

	@Test
	public void doingNothing() throws Throwable
	{
		// This test is to show that we don't generate an action if the accumulator sees nothing happening for the tick.
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData topCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData bottomCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(topCuboid, HeightMapHelpers.buildHeightMap(topCuboid));
		accumulator.setCuboid(bottomCuboid, HeightMapHelpers.buildHeightMap(bottomCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		// Run the second move and show nothing emitted.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		// Note that we slip a little here due to rounding errors not seeing the collision.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.2f), listener.thisEntity.velocity());
		
		// Run a third to see that the collision rounding error goes away.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}


	private Entity _runFallingTest(long millisPerMove, int iterationCount, CuboidData cuboid, Entity entity)
	{
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(cuboid, HeightMapHelpers.buildHeightMap(cuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation(currentTimeMillis);
		
		// Run the standing sequences and return the final entity state.
		for (int i = 0; i < iterationCount; ++i)
		{
			currentTimeMillis += millisPerMove;
			EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
			if (null != out)
			{
				entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
			}
			accumulator.applyLocalAccumulation(currentTimeMillis);
		}
		return listener.thisEntity;
	}

	private Entity _applyToEntity(long millisPerTick
			, long currentTickTimeMillis
			, List<IReadOnlyCuboidData> cuboids
			, Entity inputEntity
			, IMutationEntity<IMutablePlayerEntity> action
			, MovementAccumulator accumulator
			, _ProjectionListener listener
	)
	{
		TickProcessingContext context = _createContext(millisPerTick, currentTickTimeMillis, cuboids);
		MutableEntity mutable = MutableEntity.existing(inputEntity);
		Assert.assertTrue(action.applyChange(context, mutable));
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		return entity;
	}

	private TickProcessingContext _createContext(long millisPerTick, long currentTickTimeMillis, List<IReadOnlyCuboidData> cuboidList)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		for (IReadOnlyCuboidData cuboid : cuboidList)
		{
			cuboids.put(cuboid.getCuboidAddress(), cuboid);
		}
		return new TickProcessingContext(1L
			, (AbsoluteLocation location) -> {
				CuboidAddress address = location.getCuboidAddress();
				IReadOnlyCuboidData cuboid = cuboids.get(address);
				return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
				;
			}
			, null
			, null
			, null
			, null
			, null
			, null
			, null
			, null
			, millisPerTick
			, currentTickTimeMillis
		);
	}


	private static class _ProjectionListener implements IProjectionListener
	{
		public Entity thisEntity = null;
		public Map<Integer, PartialEntity> otherEnties = new HashMap<>();
		public Map<CuboidAddress, IReadOnlyCuboidData> loadedCuboids = new HashMap<>();
		public Map<CuboidColumnAddress, ColumnHeightMap> heightMaps = new HashMap<>();
		public int cuboidChangeCount = 0;
		public List<EventRecord> events = new ArrayList<>();
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, CuboidHeightMap cuboidHeightMap, ColumnHeightMap columnHeightMap)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertFalse(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
			this.heightMaps.put(cuboidAddress.getColumn(), columnHeightMap);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, CuboidHeightMap cuboidHeightMap
				, ColumnHeightMap columnHeightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertTrue(this.loadedCuboids.containsKey(cuboidAddress));
			Assert.assertFalse(changedBlocks.isEmpty());
			Assert.assertFalse(changedAspects.isEmpty());
			this.loadedCuboids.put(cuboidAddress, cuboid);
			this.heightMaps.put(cuboidAddress.getColumn(), columnHeightMap);
			this.cuboidChangeCount += 1;
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			int id = authoritativeEntity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNull(this.thisEntity);
			this.thisEntity = authoritativeEntity;
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			int id = projectedEntity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			Assert.assertNotNull(this.thisEntity);
			this.thisEntity = projectedEntity;
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			int id = entity.id();
			Assert.assertFalse(this.otherEnties.containsKey(id));
			this.otherEnties.put(id, entity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			int id = entity.id();
			Assert.assertTrue(this.otherEnties.containsKey(id));
			this.otherEnties.put(id, entity);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			Assert.assertTrue(this.otherEnties.containsKey(id));
			this.otherEnties.remove(id);
		}
		@Override
		public void tickDidComplete(long gameTick)
		{
			Assert.fail();
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			this.events.add(event);
		}
	}
}
