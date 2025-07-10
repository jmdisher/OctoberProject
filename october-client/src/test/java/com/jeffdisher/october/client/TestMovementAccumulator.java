package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import com.jeffdisher.october.data.MutableBlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.mutations.EntityChangeCraft;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationBlock;
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
	private static Item LOG_ITEM;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
		LOG_ITEM = ENV.items.getItemById("op.log");
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
		accumulator.clearAccumulation();
		
		// Set our orientation and stand around until the action is generated.
		byte yaw = 5;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		currentTimeMillis += 50L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
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
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		// (set the cuboids after initialization to verify that this out-of-order start-up still works)
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		
		// Walk until the action is generated.
		currentTimeMillis += 50L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.2f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 0.0f), listener.thisEntity.velocity());
		currentTimeMillis += 60L;
		out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.0f), listener.thisEntity.location());
		// Motion too little to detect collision.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.1f), listener.thisEntity.velocity());
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
		accumulator.clearAccumulation();
		
		boolean didJump = accumulator.enqueueSubAction(new EntityChangeJump<>());
		Assert.assertTrue(didJump);
		currentTimeMillis += 20L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.09f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.7f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeJump);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.4f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 3.92f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.44f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 3.82f), listener.thisEntity.velocity());
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
		accumulator.clearAccumulation();
		
		boolean didJump = accumulator.enqueueSubAction(new EntityChangeJump<>());
		Assert.assertTrue(didJump);
		currentTimeMillis += 20L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.08f, 0.09f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 4.7f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeJump);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.4f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 3.92f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.44f, 0.44f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 3.82f), listener.thisEntity.velocity());
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
		accumulator.clearAccumulation();
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.04f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.59f), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(topCuboid, bottomCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.08f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.18f), listener.thisEntity.velocity());
	}

	@Test
	public void fallingThroughWater() throws Throwable
	{
		// Show accounting for changing terminal velocity when falling from air into water.
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData topCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData bottomCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), WATER_SOURCE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(16.0f, 16.0f, 2.0f);
		mutable.newVelocity = new EntityLocation(0.0f, 0.0f, EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(topCuboid, HeightMapHelpers.buildHeightMap(topCuboid));
		accumulator.setCuboid(bottomCuboid, HeightMapHelpers.buildHeightMap(bottomCuboid));
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, 2.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -0.4f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(topCuboid, bottomCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -2.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
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
		accumulator.clearAccumulation();
		
		// Swim, then move forward until the action is generated, and again to see that the swim impacts the second action.
		boolean didSwim = accumulator.enqueueSubAction(new EntityChangeSwim<>());
		Assert.assertTrue(didSwim);
		currentTimeMillis += 20L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(15.0f, 15.04f, 15.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.8f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeSwim);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(15.0f, 15.2f, 15.45f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.41f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(15.0f, 15.22f, 15.49f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.36f), listener.thisEntity.velocity());
		
		// We now want to coast for a bit and see how they move.
		currentTimeMillis += 50L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(15.0f, 15.32f, 15.7f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 4.12f), listener.thisEntity.velocity());
		
		currentTimeMillis += 50L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(15.0f, 15.4f, 15.86f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 2.0f, 3.92f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(15.0f, 15.41f, 15.9f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.5f, 3.87f), listener.thisEntity.velocity());
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
		accumulator.clearAccumulation();
		
		// Place a block and verify that the output information is correct for local accumulation.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		accumulator.enqueueSubAction(new MutationPlaceSelectedBlock(new AbsoluteLocation(15, 15, 15), new AbsoluteLocation(15, 16, 15)));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		
		// We should now verify that the local accumulation't output shows the inventory empty, the block placed, and the height map correct.
		Assert.assertEquals(0, listener.thisEntity.inventory().currentEncumbrance);
		Assert.assertEquals(STONE.item().number(), listener.loadedCuboids.get(cuboid.getCuboidAddress()).getData15(AspectRegistry.BLOCK, BlockAddress.fromInt(15, 15, 15)));
		Assert.assertEquals(63, listener.heightMaps.get(cuboid.getCuboidAddress().getColumn()).getHeight(15, 15));
		Assert.assertEquals(1, listener.cuboidChangeCount);
		
		// Verify that this remains correct after the action is applied.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof MutationPlaceSelectedBlock);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid, blockingCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		
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
		accumulator.clearAccumulation();
		
		// Run the first move.
		long millisPerMove = 60L;
		currentTimeMillis += millisPerMove;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		// Run the second move and show nothing emitted.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		// Run a third to see that the collision rounding error goes away.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}

	@Test
	public void tinyOverflow() throws Throwable
	{
		// Show that 1 ms of overflow into the following tick doesn't cause a failure due to thinking we are standing)
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		// (set the cuboids after initialization to verify that this out-of-order start-up still works)
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		
		// Walk until the action is generated.
		currentTimeMillis += 50L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.2f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 0.0f), listener.thisEntity.velocity());
		// We spill by only 1 ms since that will cause any movement to round down while the velocity is still set.
		currentTimeMillis += 51L;
		out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(0.0f, 0.4f, 0.0f), listener.thisEntity.location());
		// Motion too little to detect collision.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.01f), listener.thisEntity.velocity());
	}

	@Test
	public void interruptCraft() throws Throwable
	{
		// Show that a partial crafting operation will be abandoned if the following tick doesn't continue it.
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData blockingCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		CuboidHeightMap cuboidMap = HeightMapHelpers.buildHeightMap(cuboid);
		CuboidHeightMap blockingMap = HeightMapHelpers.buildHeightMap(blockingCuboid);
		ColumnHeightMap columnMap = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), cuboidMap
			, blockingCuboid.getCuboidAddress(), blockingMap
		)).values().iterator().next();
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newInventory.addAllItems(LOG_ITEM, 1);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(cuboid, cuboidMap);
		accumulator.setCuboid(blockingCuboid, blockingMap);
		listener.thisEntityDidLoad(entity);
		listener.cuboidDidLoad(cuboid, cuboidMap, columnMap);
		listener.cuboidDidLoad(blockingCuboid, blockingMap, columnMap);
		accumulator.clearAccumulation();
		
		// Enqueue a craft operation and run a standing iteration to see that it does set the local crafting operation but that the following tick, without continuing, abandons it.
		long millisPerMove = millisPerTick;
		currentTimeMillis += millisPerMove;
		accumulator.enqueueSubAction(new EntityChangeCraft(ENV.crafting.getCraftById("op.log_to_planks")));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeCraft);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid, blockingCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertNotNull(listener.thisEntity.localCraftOperation());
		
		// Now, run another standing tick and see that it is dropped.
		// NOTE:  We need to move to force the generation of the actual action or we will be considered doing nothing and it will still wait.
		currentTimeMillis += millisPerMove;
		out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid, blockingCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertNull(listener.thisEntity.localCraftOperation());
		Assert.assertEquals(4, listener.thisEntity.inventory().currentEncumbrance);
	}

	@Test
	public void breakWhileInBlock() throws Throwable
	{
		// Show that we can break a block even when encased in stone.
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), STONE);
		CuboidHeightMap cuboidMap = HeightMapHelpers.buildHeightMap(cuboid);
		ColumnHeightMap columnMap = HeightMapHelpers.buildColumnMaps(Map.of(cuboid.getCuboidAddress(), cuboidMap
		)).values().iterator().next();
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newInventory.addAllItems(LOG_ITEM, 1);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		accumulator.setCuboid(cuboid, cuboidMap);
		listener.thisEntityDidLoad(entity);
		listener.cuboidDidLoad(cuboid, cuboidMap, columnMap);
		accumulator.clearAccumulation();
		
		// Enqueue a craft operation and run a standing iteration to see that it does set the local crafting operation but that the following tick, without continuing, abandons it.
		AbsoluteLocation targetBlock = entity.location().getBlockLocation();
		long millisPerMove = millisPerTick;
		currentTimeMillis += millisPerMove;
		accumulator.enqueueSubAction(new EntityChangeIncrementalBlockBreak(targetBlock, (short)millisPerTick));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeIncrementalBlockBreak);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals((short)100, listener.loadedCuboids.get(cuboid.getCuboidAddress()).getData15(AspectRegistry.DAMAGE, targetBlock.getBlockAddress()));
	}

	@Test
	public void errorCase0() throws Throwable
	{
		// This tests an error case observed while testing OctoberPeaks.
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(18.36f, 20.0f - 16.89f, 0.18f);
		mutable.newVelocity = new EntityLocation(0.68f, -3.94f, -3.92f);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		// (set the cuboids after initialization to verify that this out-of-order start-up still works)
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		
		currentTimeMillis += 16L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		currentTimeMillis += 16L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		currentTimeMillis += 16L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		currentTimeMillis += 16L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(18.36f, 3.02f, 0.0f), listener.thisEntity.location());
		// Motion too little to detect collision.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.14f), listener.thisEntity.velocity());
		
		currentTimeMillis += 16L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(18.36f, 3.02f, 0.0f), listener.thisEntity.location());
		// This is below the collision threshold so we still see the falling.
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.29f), listener.thisEntity.velocity());
	}

	@Test
	public void errorCase1() throws Throwable
	{
		// This tests an error case observed while testing OctoberPeaks (falling while walking in the same action as we touched down on the lip).
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		EntityLocation startLocation = new EntityLocation(5.98f, 6.0f, 7.0f);
		AbsoluteLocation blockLocation = new AbsoluteLocation(5, 6, 6);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE_ITEM.number());
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = startLocation;
		mutable.newVelocity = new EntityLocation(4.0f, 0.0f, -0.49f);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		accumulator.setCuboid(cuboid, HeightMapHelpers.buildHeightMap(cuboid));
		
		currentTimeMillis += 16L;
		accumulator.setOrientation(OrientationHelpers.YAW_WEST, OrientationHelpers.PITCH_FLAT);
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.BACKWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(6.02f, 6.0f, 7.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(2.4f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}

	@Test
	public void creativeClearsSubAction() throws Throwable
	{
		// This tests a case where the accumulator would clear queued sub-actions, when standing in creative mode, since it wouldn't change the entity, thus being detected as a failure.
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		EntityLocation startLocation = new EntityLocation(5.98f, 6.0f, 7.0f);
		AbsoluteLocation blockLocation = new AbsoluteLocation(5, 6, 6);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE_ITEM.number());
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = startLocation;
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		accumulator.setCuboid(cuboid, HeightMapHelpers.buildHeightMap(cuboid));
		
		// Enqueue and then stand around for a bit (enough that we will properly collide with the ground).
		currentTimeMillis += 25L;
		accumulator.enqueueSubAction(new EntityChangeChangeHotbarSlot(1));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		
		// Stand for a full tick and verify that it now generates the action.
		currentTimeMillis += millisPerTick;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof EntityChangeChangeHotbarSlot);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(1, listener.thisEntity.hotbarIndex());
	}

	@Test
	public void coastingAfterWalking() throws Throwable
	{
		// Tests how we handle an action where we start walking but then coast into the following action by standing (related to how we handle velocity and friction).
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		accumulator.setCuboid(airCuboid, HeightMapHelpers.buildHeightMap(airCuboid));
		accumulator.setCuboid(stoneCuboid, HeightMapHelpers.buildHeightMap(stoneCuboid));
		
		// Walk for part of a tick.
		currentTimeMillis += 40L;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.walk(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		
		// Now, just stand so we will coast into the next action.
		currentTimeMillis += 40L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertNull(out.test_getSubAction());
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		
		// Completed this action and verify that it applied correctly.
		currentTimeMillis += 40L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
	}

	@Test
	public void creativePlaceBlock() throws Throwable
	{
		// Tests an issue where placing a block would fail in creative mode since the entity isn't changing (as it doesn't have a real inventory).
		long millisPerTick = 50L;
		long currentTimeMillis = 1000L;
		EntityLocation startLocation = new EntityLocation(5.98f, 6.0f, 7.0f);
		AbsoluteLocation blockLocation = new AbsoluteLocation(5, 6, 6);
		CuboidAddress cuboidAddress = CuboidAddress.fromInt(0, 0, 0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(cuboidAddress, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE_ITEM.number());
		CuboidHeightMap cuboidHeightMap = HeightMapHelpers.buildHeightMap(cuboid);
		ColumnHeightMap columnHeightMap = HeightMapHelpers.buildColumnMaps(Map.of(cuboidAddress, cuboidHeightMap)).get(cuboidAddress.getColumn());
		
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, ENV.creatures.PLAYER.volume(), currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = startLocation;
		mutable.isCreativeMode = true;
		mutable.setSelectedKey(STONE_ITEM.number());
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		accumulator.clearAccumulation();
		accumulator.setCuboid(cuboid, cuboidHeightMap);
		listener.cuboidDidLoad(cuboid, cuboidHeightMap, columnHeightMap);
		
		// Enqueue and then stand around for a bit (enough that we will properly collide with the ground).
		currentTimeMillis += 25L;
		AbsoluteLocation targetLocation = new AbsoluteLocation(4, 5, 7);
		accumulator.enqueueSubAction(new MutationPlaceSelectedBlock(targetLocation, targetLocation.getRelative(0, 0, -1)));
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
		// Note that this will produce nothing and will actually reset the accumulation since it changes nothing about the entity - it will cue up the sub-action, though.
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		
		// Stand for the rest of the tick time such that the action is generated - it should change the cuboid.
		currentTimeMillis += 25L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		Assert.assertTrue(out.test_getSubAction() instanceof MutationPlaceSelectedBlock);
		entity = _applyToEntityAndUpdateCuboid(millisPerTick, currentTimeMillis, cuboid, entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(STONE_ITEM.number(), listener.loadedCuboids.get(targetLocation.getCuboidAddress()).getData15(AspectRegistry.BLOCK, targetLocation.getBlockAddress()));
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
		accumulator.clearAccumulation();
		
		// Run the standing sequences and return the final entity state.
		for (int i = 0; i < iterationCount; ++i)
		{
			currentTimeMillis += millisPerMove;
			EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.stand(currentTimeMillis);
			if (null != out)
			{
				Assert.assertNull(out.test_getSubAction());
				entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(cuboid), entity, out, accumulator, listener);
			}
			accumulator.applyLocalAccumulation();
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
		TickProcessingContext context = _createContext(millisPerTick, currentTickTimeMillis, cuboids, (IMutationBlock mutation) -> {
			// Do nothing - cuboid mutations are ignored in this path.
		});
		MutableEntity mutable = MutableEntity.existing(inputEntity);
		Assert.assertTrue(action.applyChange(context, mutable));
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		return entity;
	}

	private Entity _applyToEntityAndUpdateCuboid(long millisPerTick
			, long currentTickTimeMillis
			, IReadOnlyCuboidData cuboid
			, Entity inputEntity
			, IMutationEntity<IMutablePlayerEntity> action
			, MovementAccumulator accumulator
			, _ProjectionListener listener
	)
	{
		List<IMutationBlock> mutations = new ArrayList<>();
		TickProcessingContext context = _createContext(millisPerTick, currentTickTimeMillis, List.of(cuboid), (IMutationBlock mutation) -> {
			// Capture this so we can apply it.
			mutations.add(mutation);
		});
		MutableEntity mutable = MutableEntity.existing(inputEntity);
		Assert.assertTrue(action.applyChange(context, mutable));
		Entity entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		CuboidData lazyMutable = null;
		Set<BlockAddress> changedBlocks = new HashSet<>();
		for (IMutationBlock mutation : mutations)
		{
			AbsoluteLocation location = mutation.getAbsoluteLocation();
			MutableBlockProxy proxy = new MutableBlockProxy(location, cuboid);
			mutation.applyMutation(context, proxy);
			if (proxy.didChange())
			{
				if (null == lazyMutable)
				{
					lazyMutable = CuboidData.mutableClone(cuboid);
				}
				proxy.writeBack(lazyMutable);
				changedBlocks.add(location.getBlockAddress());
			}
		}
		if (null != lazyMutable)
		{
			CuboidAddress address = lazyMutable.getCuboidAddress();
			CuboidHeightMap cuboidHeightMap = HeightMapHelpers.buildHeightMap(lazyMutable);
			ColumnHeightMap columnHeightMap = HeightMapHelpers.buildColumnMaps(Map.of(address, cuboidHeightMap)).get(address.getColumn());
			accumulator.setCuboid(lazyMutable, cuboidHeightMap);
			// To keep things simple for these tests, we assume only block aspect changes.
			listener.cuboidDidChange(lazyMutable, cuboidHeightMap, columnHeightMap, changedBlocks, Set.of(AspectRegistry.BLOCK));
		}
		return entity;
	}

	private TickProcessingContext _createContext(long millisPerTick, long currentTickTimeMillis, List<IReadOnlyCuboidData> cuboidList, Consumer<IMutationBlock> mutationSink)
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
			, new TickProcessingContext.IMutationSink() {
				@Override
				public void next(IMutationBlock mutation)
				{
					// Pass this out to be used elsewhere.
					mutationSink.accept(mutation);
				}
				@Override
				public void future(IMutationBlock mutation, long millisToDelay)
				{
					// Do nothing.
				}
			}
			, null
			, null
			, null
			, (EventRecord event) -> {
				// For now, we will just drop these but may want them in the future.
			}
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
