package com.jeffdisher.october.utils;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.MutableEntity;


public class TestLazyEntityIndex
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		COW = ENV.creatures.getTypeById("op.cow");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty()
	{
		LazyEntityIndex entityIndex = new LazyEntityIndex(Map.of(), Map.of());
		Assert.assertNull(entityIndex.getById(1));
		Assert.assertNull(entityIndex.getById(-1));
		Assert.assertEquals(0, entityIndex.findEntityIdsInRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f)).length);
	}

	@Test
	public void playersOnly()
	{
		MutableEntity entity1 = MutableEntity.createForTest(1);
		entity1.newLocation = new EntityLocation(1.0f, 2.0f, 3.0f);
		MutableEntity entity2 = MutableEntity.createForTest(2);
		entity2.newLocation = new EntityLocation(-1.0f, -2.2f, 3.1f);
		LazyEntityIndex entityIndex = new LazyEntityIndex(Map.of(1, entity1.freeze(), 2, entity2.freeze()), Map.of());
		Assert.assertNotNull(entityIndex.getById(1));
		Assert.assertNull(entityIndex.getById(-1));
		Assert.assertEquals(2, entityIndex.findEntityIdsInRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f)).length);
		Assert.assertEquals(0, entityIndex.findEntityIdsInRegion(new EntityLocation(100.0f, 100.0f, 100.0f), new EntityLocation(200.0f, 200.0f, 200.0f)).length);
	}

	@Test
	public void singleCreatureType()
	{
		CreatureEntity creature1 = CreatureEntity.create(-1
			, COW
			, new EntityLocation(1.0f, -2.4f, 5.6f)
			, 1000L
		);
		CreatureEntity creature2 = CreatureEntity.create(-2
			, COW
			, new EntityLocation(1.5f, -1.9f, 5.2f)
			, 1000L
		);
		LazyEntityIndex entityIndex = new LazyEntityIndex(Map.of(), Map.of(creature1.id(), creature1, creature2.id(), creature2));
		Assert.assertNull(entityIndex.getById(1));
		Assert.assertNotNull(entityIndex.getById(-1));
		Assert.assertEquals(2, entityIndex.findEntityIdsInRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f)).length);
		Assert.assertEquals(0, entityIndex.findEntityIdsInRegion(new EntityLocation(100.0f, 100.0f, 100.0f), new EntityLocation(200.0f, 200.0f, 200.0f)).length);
	}

	@Test
	public void mixOfTypes()
	{
		EntityType orc = ENV.creatures.getTypeById("op.orc");
		CreatureEntity creature1 = CreatureEntity.create(-1
			, COW
			, new EntityLocation(1.0f, -2.4f, 5.6f)
			, 1000L
		);
		CreatureEntity creature2 = CreatureEntity.create(-2
			, orc
			, new EntityLocation(1.5f, -1.9f, 5.2f)
			, 1000L
		);
		LazyEntityIndex entityIndex = new LazyEntityIndex(Map.of(), Map.of(creature1.id(), creature1, creature2.id(), creature2));
		Assert.assertNull(entityIndex.getById(1));
		Assert.assertNotNull(entityIndex.getById(-1));
		Assert.assertEquals(2, entityIndex.findEntityIdsInRegion(new EntityLocation(-100.0f, -100.0f, -100.0f), new EntityLocation(100.0f, 100.0f, 100.0f)).length);
		Assert.assertEquals(0, entityIndex.findEntityIdsInRegion(new EntityLocation(100.0f, 100.0f, 100.0f), new EntityLocation(200.0f, 200.0f, 200.0f)).length);
	}
}
