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
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestMovementAccumulator
{
	public static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	private static Block STONE;
	private static Block WATER_SOURCE;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
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
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 0.0f), listener.thisEntity.velocity());
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
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.9f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.49f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.9f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.53f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 3.92f), listener.thisEntity.velocity());
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
		Assert.assertEquals(new EntityLocation(0.0f, 0.48f, 0.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 4.9f), listener.thisEntity.velocity());
		
		currentTimeMillis += 90L;
		out = accumulator.move(currentTimeMillis, EntityChangeTopLevelMovement.Relative.FORWARD);
		Assert.assertNotNull(out);
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(airCuboid, stoneCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.8f, 0.49f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 4.9f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.84f, 0.53f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 4.0f, 3.92f), listener.thisEntity.velocity());
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
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.06f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.stand(currentTimeMillis);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		entity = _applyToEntity(millisPerTick, currentTimeMillis, List.of(topCuboid, bottomCuboid), entity, out, accumulator, listener);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.1f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.98f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.14f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.96f), listener.thisEntity.velocity());
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
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -1.2f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -20.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(16.0f, 16.0f, -1.41f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -20.98f), listener.thisEntity.velocity());
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
		public List<EventRecord> events = new ArrayList<>();
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertFalse(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertTrue(this.loadedCuboids.containsKey(cuboidAddress));
			Assert.assertFalse(changedBlocks.isEmpty());
			Assert.assertFalse(changedAspects.isEmpty());
			this.loadedCuboids.put(cuboidAddress, cuboid);
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
