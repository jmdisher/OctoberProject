package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
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
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicMove() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		
		// Run the first move.
		currentTimeMillis += 50L;
		EntityLocation location = new EntityLocation(1.0f, 0.0f, 0.0f);
		EntityLocation velocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		byte yaw = 5;
		byte pitch = 6;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, location, velocity, yaw, pitch, true);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(1.0f, 0.0f, 0.0f), listener.thisEntity.location());
		
		// Run the second move.
		currentTimeMillis += 60L;
		location = new EntityLocation(2.2f, 0.0f, 0.0f);
		velocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		out = accumulator.move(currentTimeMillis, location, velocity, yaw, pitch, true);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		TickProcessingContext context = _createContext(millisPerTick, currentTimeMillis);
		MutableEntity mutable = MutableEntity.existing(entity);
		Assert.assertTrue(out.applyChange(context, mutable));
		entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		
		// See the last bit of the movement applied local
		Assert.assertEquals(new EntityLocation(2.0f, 0.0f, 0.0f), listener.thisEntity.location());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(2.2f, 0.0f, 0.0f), listener.thisEntity.location());
	}

	@Test
	public void jumpInMove() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		
		// Run the first move.
		currentTimeMillis += 50L;
		EntityLocation location = new EntityLocation(1.0f, 0.0f, 0.0f);
		EntityLocation velocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		byte yaw = 5;
		byte pitch = 6;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, location, velocity, yaw, pitch, true);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(1.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(5.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		// Add a jump, and show that adding a second jump will be ignored.
		out = accumulator.jump(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(1.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(5.0f, 0.0f, 4.9f), listener.thisEntity.velocity());
		out = accumulator.jump(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(1.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(5.0f, 0.0f, 4.9f), listener.thisEntity.velocity());
		
		// Run the second move.
		currentTimeMillis += 60L;
		location = new EntityLocation(2.2f, 0.0f, 0.0f);
		velocity = new EntityLocation(5.0f, 0.0f, 0.0f);
		out = accumulator.move(currentTimeMillis, location, velocity, yaw, pitch, true);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		TickProcessingContext context = _createContext(millisPerTick, currentTimeMillis);
		MutableEntity mutable = MutableEntity.existing(entity);
		Assert.assertTrue(out.applyChange(context, mutable));
		entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		
		// See the last bit of the movement applied local
		Assert.assertEquals(new EntityLocation(2.0f, 0.0f, 0.0f), listener.thisEntity.location());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(2.2f, 0.0f, 0.0f), listener.thisEntity.location());
	}

	@Test
	public void fallingThroughAir() throws Throwable
	{
		long millisPerTick = 100L;
		long currentTimeMillis = 1000L;
		_ProjectionListener listener = new _ProjectionListener();
		MovementAccumulator accumulator = new MovementAccumulator(listener, millisPerTick, currentTimeMillis);
		
		// Create the baseline data we need.
		Entity entity = MutableEntity.createForTest(1).freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidLoad(entity);
		
		// Simulate falling to see how to update the location and velocity.
		long millisPerMove = 75L;
		CuboidData fakeCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		Function<AbsoluteLocation, BlockProxy> airLookup = (AbsoluteLocation location) -> new BlockProxy(location.getBlockAddress(), fakeCuboid);
		MutableEntity fake = MutableEntity.existing(listener.thisEntity);
		EntityMovementHelpers.allowMovement(airLookup, fake, millisPerMove);
		EntityLocation location1 = fake.newLocation;
		EntityLocation velocity1 = fake.newVelocity;
		
		// Run the first move.
		currentTimeMillis += millisPerMove;
		byte yaw = 5;
		byte pitch = 6;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> out = accumulator.move(currentTimeMillis, location1, velocity1, yaw, pitch, false);
		Assert.assertNull(out);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.03f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.73f), listener.thisEntity.velocity());
		
		fake = MutableEntity.existing(listener.thisEntity);
		EntityMovementHelpers.allowMovement(airLookup, fake, millisPerMove);
		EntityLocation location2 = fake.newLocation;
		EntityLocation velocity2 = fake.newVelocity;
		
		// Run the second move.
		currentTimeMillis += millisPerMove;
		out = accumulator.move(currentTimeMillis, location2, velocity2, yaw, pitch, false);
		Assert.assertNotNull(out);
		
		// We need to apply this to our state since it would be considered part of the underlying state.
		TickProcessingContext context = _createContext(millisPerTick, currentTimeMillis);
		MutableEntity mutable = MutableEntity.existing(entity);
		Assert.assertTrue(out.applyChange(context, mutable));
		entity = mutable.freeze();
		accumulator.setThisEntity(entity);
		listener.thisEntityDidChange(entity, entity);
		
		// See the last bit of the movement applied local
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.06f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.97f), listener.thisEntity.velocity());
		accumulator.applyLocalAccumulation(currentTimeMillis);
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.11f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -1.46f), listener.thisEntity.velocity());
	}


	private TickProcessingContext _createContext(long millisPerTick, long currentTickTimeMillis)
	{
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		CuboidData stoneCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), STONE);
		return new TickProcessingContext(1L
			, (AbsoluteLocation location) -> {
				CuboidAddress address = location.getCuboidAddress();
				BlockProxy proxy;
				if (address.equals(airCuboid.getCuboidAddress()))
				{
					proxy = new BlockProxy(location.getBlockAddress(), airCuboid);
				}
				else if (address.equals(stoneCuboid.getCuboidAddress()))
				{
					proxy = new BlockProxy(location.getBlockAddress(), stoneCuboid);
				}
				else
				{
					proxy = null;
				}
				return proxy;
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
