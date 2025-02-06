package com.jeffdisher.october.logic;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.MutableEntity;


public class TestEntityCollection
{
	private static Environment ENV;
	private static EntityType COW;
	@BeforeClass
	public static void setup()
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
	public void checkEmpty()
	{
		Map<Integer, Entity> players = Map.of();
		Map<Integer, CreatureEntity> creatures = Map.of();
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		int count = collection.walkPlayersInRange(centre, 1.0f, (Entity player) -> Assert.fail());
		Assert.assertEquals(0, count);
		count = collection.walkCreaturesInRange(centre, 1.0f, (CreatureEntity creature) -> Assert.fail());
		Assert.assertEquals(0, count);
	}

	@Test
	public void noneInRange()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, 1.0f, 1.0f)));
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f)));
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		int count = collection.walkPlayersInRange(centre, 1.0f, (Entity player) -> Assert.fail());
		Assert.assertEquals(0, count);
		count = collection.walkCreaturesInRange(centre, 1.0f, (CreatureEntity creature) -> Assert.fail());
		Assert.assertEquals(0, count);
	}

	@Test
	public void someInRange()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, -1.0f, 1.0f))
				, 2, _buildPlayer(2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f))
				, -2, _buildCreature(-2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		int[] counts = new int[2];
		int count = collection.walkPlayersInRange(centre, 3.0f, (Entity player) -> counts[0] += 1);
		Assert.assertEquals(2, count);
		count = collection.walkCreaturesInRange(centre, 3.0f, (CreatureEntity creature) -> counts[1] += 1);
		Assert.assertEquals(2, count);
		
		Assert.assertEquals(2, counts[0]);
		Assert.assertEquals(2, counts[1]);
	}

	@Test
	public void directQueries()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, -1.0f, 1.0f))
				, 2, _buildPlayer(2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f))
				, -2, _buildCreature(-2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		EntityCollection collection = new EntityCollection(players, creatures);
		Assert.assertNotNull(collection.getPlayerById(1));
		Assert.assertNotNull(collection.getPlayerById(2));
		Assert.assertNull(collection.getPlayerById(3));
		Assert.assertNotNull(collection.getCreatureById(-1));
		Assert.assertNotNull(collection.getCreatureById(-2));
		Assert.assertNull(collection.getCreatureById(-3));
	}


	private static Entity _buildPlayer(int id, EntityLocation location)
	{
		return new Entity(id
				, false
				, location
				, new EntityLocation(0.0f, 0.0f, 0.0f)
				, (byte)0
				, (byte)0
				, null
				, null
				, 0
				, null
				, null
				, (byte)0
				, (byte)0
				, MiscConstants.MAX_BREATH
				, 0
				, MutableEntity.TESTING_LOCATION
				, Entity.EMPTY_DATA
		);
	}

	private static CreatureEntity _buildCreature(int id, EntityLocation location)
	{
		return CreatureEntity.create(id, COW, location, (byte)10);
	}
}
