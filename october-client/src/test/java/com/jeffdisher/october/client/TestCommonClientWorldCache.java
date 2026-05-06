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
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCommonClientWorldCache
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
	public void addUpdateRemove() throws Throwable
	{
		// This just shows that we can correctly handle add/update/remove actions related to cuboids, creatures, and passives.
		long millisPerTick = 100L;
		_ProjectionListener listener = new _ProjectionListener();
		CommonClientWorldCache commonCache = new CommonClientWorldCache(ENV, listener, millisPerTick);
		
		// Set the entity (just for completeness).  This is normally set first.
		MutableEntity mutable = MutableEntity.createForTest(1);
		mutable.newLocation = new EntityLocation(5.8f, 5.8f, 6.0f);
		Entity entity = mutable.freeze();
		commonCache.setThisEntity(entity);
		
		// Create, update, remove a cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		commonCache.setCuboid(cuboid, Set.of());
		// (these are just set again).
		commonCache.setCuboid(cuboid, Set.of());
		commonCache.removeCuboid(cuboid.getCuboidAddress());
		
		// Create, update, remove other entity (either a creature or other player).
		PartialEntity partial = PartialEntity.fromCreature(CreatureEntity.create(-1, ENV.creatures.PLAYER, new EntityLocation(1.2f, -2.3f, 3.4f), 0L));
		commonCache.setOtherEntity(partial);
		// (these are just set again).
		commonCache.setOtherEntity(partial);
		commonCache.removeOtherEntity(partial.id());
		
		// Create, update, remove passive entity.
		PartialPassive passive = new PartialPassive(1
			, PassiveType.ITEM_SLOT
			, new EntityLocation(1.2f, 2.3f, -3.4f)
			, new EntityLocation(0.0f, 0.0f, -0.5f)
			, null
		);
		commonCache.addPassive(passive);
		commonCache.updatePassive(passive);
		commonCache.removePassive(passive.id());
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
