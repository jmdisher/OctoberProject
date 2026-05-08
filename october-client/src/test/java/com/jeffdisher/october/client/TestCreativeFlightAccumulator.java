package com.jeffdisher.october.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.actions.EntityActionCreativeFlight;
import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCreativeFlightAccumulator
{
	public static final long MILLIS_PER_TICK = 100L;
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void hoverSmallIncrements() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = 5;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		for (int i = 0; i < 19; ++i)
		{
			currentTimeMillis += 5L;
			EntityActionCreativeFlight out = accumulator.hover(currentTimeMillis);
			Assert.assertNull(out);
			accumulator.applyLocalAccumulation();
			Assert.assertEquals(entity.location(), listener.thisEntity.location());
			Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		}
		
		currentTimeMillis += 5L;
		EntityActionCreativeFlight out = accumulator.hover(currentTimeMillis);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 0);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}

	@Test
	public void hoverLargeIncrements() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = 5;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		currentTimeMillis += 60L;
		EntityActionCreativeFlight out = accumulator.hover(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		currentTimeMillis += 60L;
		out = accumulator.hover(currentTimeMillis);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 0);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}

	@Test
	public void flySmallIncrements() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = 64;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		float vY = 0.0f;
		float vZ = 0.0f;
		for (int i = 0; i < 19; ++i)
		{
			currentTimeMillis += 5L;
			EntityActionCreativeFlight out = accumulator.fly(currentTimeMillis, RelativeDirection.RIGHT, VerticalDirection.UP);
			Assert.assertNull(out);
			accumulator.applyLocalAccumulation();
			Assert.assertEquals(0.0f, listener.thisEntity.velocity().x(), 0.01f);
			Assert.assertTrue(listener.thisEntity.velocity().y() > vY);
			vY = listener.thisEntity.velocity().y();
			Assert.assertTrue(listener.thisEntity.velocity().z() > vZ);
			vZ = listener.thisEntity.velocity().z();
		}
		Assert.assertEquals(new EntityLocation(10.0f, 10.51f, 10.51f), listener.thisEntity.location());
		
		currentTimeMillis += 5L;
		EntityActionCreativeFlight out = accumulator.fly(currentTimeMillis, RelativeDirection.RIGHT, VerticalDirection.UP);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 0);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(10.0f, 10.57f, 10.57f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 5.66f, 5.66f), listener.thisEntity.velocity());
	}

	@Test
	public void flyLargeIncrements() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = -64;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		currentTimeMillis += 60L;
		EntityActionCreativeFlight out = accumulator.fly(currentTimeMillis, RelativeDirection.RIGHT, VerticalDirection.UP);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(10.0f, 9.8f, 10.2f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, -3.41f, 3.41f), listener.thisEntity.velocity());
		
		currentTimeMillis += 60L;
		out = accumulator.fly(currentTimeMillis, RelativeDirection.RIGHT, VerticalDirection.UP);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 0);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(10.0f, 9.35f, 10.65f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, -3.97f, 3.97f), listener.thisEntity.velocity());
	}

	@Test
	public void flyStraightUp() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = -64;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		
		currentTimeMillis += 60L;
		EntityActionCreativeFlight out = accumulator.fly(currentTimeMillis, null, VerticalDirection.UP);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 10.29f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 4.8f), listener.thisEntity.velocity());
		
		currentTimeMillis += 60L;
		out = accumulator.fly(currentTimeMillis, null, VerticalDirection.UP);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 0);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 10.91f), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 5.6f), listener.thisEntity.velocity());
	}

	@Test
	public void placeBlock() throws Throwable
	{
		long currentTimeMillis = 1000L;
		CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, MILLIS_PER_TICK);
		CreativeFlightAccumulator accumulator = new CreativeFlightAccumulator(commonCache, currentTimeMillis);
		
		// Create the baseline data we need.
		int creativeStoneKey = 1;
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(10.0f, 10.0f, 10.0f);
		mutable.isCreativeMode = true;
		mutable.setSelectedKey(creativeStoneKey);
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		commonCache.setCuboid(airCuboid, Set.of());
		listener.thisEntityDidLoad(entity);
		
		byte yaw = 5;
		byte pitch = 6;
		accumulator.setOrientation(yaw, pitch);
		boolean didAdd = accumulator.enqueueSubAction(currentTimeMillis, new MutationPlaceSelectedBlock(entity.location().getBlockLocation().getRelative(1, 0, 0), null));
		Assert.assertTrue(didAdd);
		
		currentTimeMillis += 60L;
		EntityActionCreativeFlight out = accumulator.hover(currentTimeMillis);
		Assert.assertNull(out);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
		
		currentTimeMillis += 60L;
		out = accumulator.hover(currentTimeMillis);
		entity = _applyToEntity(MILLIS_PER_TICK, currentTimeMillis, List.of(airCuboid), entity, out, commonCache, listener, 1);
		accumulator.applyLocalAccumulation();
		Assert.assertEquals(entity.location(), listener.thisEntity.location());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), listener.thisEntity.velocity());
	}


	private Entity _applyToEntity(long millisPerTick
		, long currentTickTimeMillis
		, List<IReadOnlyCuboidData> cuboids
		, Entity inputEntity
		, IEntityAction<IMutablePlayerEntity> action
		, CommonClientWorldCache commonCache
		, _ProjectionListener listener
		, int expectedMutations
	)
	{
		int[] mutations = new int[1];
		TickProcessingContext context = _createContext(millisPerTick, currentTickTimeMillis, cuboids, (IMutationBlock mutation) -> {
			mutations[0] += 1;
		});
		MutableEntity mutable = MutableEntity.existing(inputEntity);
		Assert.assertTrue(action.applyChange(context, mutable));
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		listener.thisEntityDidChange(entity);
		Assert.assertEquals(expectedMutations, mutations[0]);
		return entity;
	}

	private TickProcessingContext _createContext(long millisPerTick, long currentTickTimeMillis, List<IReadOnlyCuboidData> cuboidList, Consumer<IMutationBlock> mutationSink)
	{
		Map<CuboidAddress, IReadOnlyCuboidData> cuboids = new HashMap<>();
		for (IReadOnlyCuboidData cuboid : cuboidList)
		{
			cuboids.put(cuboid.getCuboidAddress(), cuboid);
		}
		TickProcessingContext.IBlockFetcher blockFetcher = new TickProcessingContext.IBlockFetcher() {
			@Override
			public BlockProxy readBlock(AbsoluteLocation location)
			{
				return _readBlock(location);
			}
			@Override
			public Map<AbsoluteLocation, BlockProxy> readBlockBatch(Collection<AbsoluteLocation> locations)
			{
				Map<AbsoluteLocation, BlockProxy> completed = new HashMap<>();
				for (AbsoluteLocation location : locations)
				{
					BlockProxy proxy = _readBlock(location);
					if (null != proxy)
					{
						completed.put(location, proxy);
					}
				}
				return completed;
			}
			private BlockProxy _readBlock(AbsoluteLocation location)
			{
				CuboidAddress address = location.getCuboidAddress();
				IReadOnlyCuboidData cuboid = cuboids.get(address);
				return (null != cuboid)
					? BlockProxy.load(location.getBlockAddress(), cuboid)
					: null
				;
			}
		};
		return new TickProcessingContext(1L
			, blockFetcher
			, null
			, null
			, null
			, null
			, new TickProcessingContext.IMutationSink() {
				@Override
				public boolean next(IMutationBlock mutation)
				{
					// Pass this out to be used elsewhere.
					mutationSink.accept(mutation);
					return true;
				}
				@Override
				public boolean future(IMutationBlock mutation, long millisToDelay)
				{
					// Do nothing.
					return true;
				}
			}
			, null
			, null
			, null
			, null
			, (EventRecord event) -> {
				// For now, we will just drop these but may want them in the future.
			}
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
		public List<EventRecord> events = new ArrayList<>();
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap columnHeightMap)
		{
			System.out.println("LOAD");
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertFalse(this.loadedCuboids.containsKey(cuboidAddress));
			this.loadedCuboids.put(cuboidAddress, cuboid);
			this.heightMaps.put(cuboidAddress.getColumn(), columnHeightMap);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
			, ColumnHeightMap columnHeightMap
			, Set<BlockAddress> changedBlocks
			, Set<Aspect<?, ?>> changedAspects
		)
		{
			System.out.println("CUBOID");
			CuboidAddress cuboidAddress = cuboid.getCuboidAddress();
			Assert.assertTrue(this.loadedCuboids.containsKey(cuboidAddress));
			Assert.assertFalse(changedBlocks.isEmpty());
			Assert.assertFalse(changedAspects.isEmpty());
			this.loadedCuboids.put(cuboidAddress, cuboid);
			this.heightMaps.put(cuboidAddress.getColumn(), columnHeightMap);
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
		public void thisEntityDidChange(Entity projectedEntity)
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
		public void passiveEntityDidLoad(PartialPassive entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void passiveEntityDidChange(PartialPassive entity)
		{
			throw new AssertionError("Not in test");
		}
		@Override
		public void passiveEntityDidUnload(int id)
		{
			throw new AssertionError("Not in test");
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
