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
import com.jeffdisher.october.types.MutableCreature;
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
		// These tests assume that they know the cow's view distance.
		Assert.assertEquals(7.0f, COW.viewDistance(), 0.01f);
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
		MutableCreature searcher = _buildMutableCow(-1, centre);
		int count = collection.countPlayersInRangeOfBase(centre, 1.0f);
		Assert.assertEquals(0, count);
		count = collection.walkPlayersInViewDistance(searcher, (Entity player) -> Assert.fail());
		Assert.assertEquals(0, count);
		count = collection.walkCreaturesInViewDistance(searcher, (CreatureEntity creature) -> Assert.fail());
		Assert.assertEquals(0, count);
	}

	@Test
	public void noneInRange()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(10.0f, 1.0f, 1.0f)));
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-10.0f, -1.0f, 1.0f)));
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature searcher = _buildMutableCow(-1, centre);
		int count = collection.countPlayersInRangeOfBase(centre, 1.0f);
		Assert.assertEquals(0, count);
		count = collection.walkPlayersInViewDistance(searcher, (Entity player) -> Assert.fail());
		Assert.assertEquals(0, count);
		count = collection.walkCreaturesInViewDistance(searcher, (CreatureEntity creature) -> Assert.fail());
		Assert.assertEquals(0, count);
	}

	@Test
	public void someInRange()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, -1.0f, 1.0f))
				, 2, _buildPlayer(2, new EntityLocation(-1.0f, 1.0f, 1.0f))
				, 3, _buildPlayer(3, new EntityLocation(-10.0f, 1.0f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f))
				, -2, _buildCreature(-2, new EntityLocation(-1.0f, 1.0f, 1.0f))
				, -3, _buildCreature(-3, new EntityLocation(-10.0f, 1.0f, 1.0f))
		);
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		MutableCreature searcher = _buildMutableCow(-1, centre);
		int[] counts = new int[2];
		int count = collection.countPlayersInRangeOfBase(centre, COW.viewDistance());
		Assert.assertEquals(2, count);
		count = collection.walkPlayersInViewDistance(searcher, (Entity player) -> counts[0] += 1);
		Assert.assertEquals(2, count);
		count = collection.walkCreaturesInViewDistance(searcher, (CreatureEntity creature) -> counts[1] += 1);
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

	@Test
	public void intersections()
	{
		Map<Integer, Entity> players = Map.of(1, _buildPlayer(1, new EntityLocation(1.0f, -1.0f, 1.0f))
				, 2, _buildPlayer(2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		Map<Integer, CreatureEntity> creatures = Map.of(-1, _buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f))
				, -2, _buildCreature(-2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		
		int[] counts = new int[2];
		EntityCollection.IIntersector<Entity> entityCounter = (Entity data, EntityLocation centre, float radius) -> {
			counts[0] += 1;
		};
		EntityCollection.IIntersector<CreatureEntity> creatureCounter = (CreatureEntity data, EntityLocation centre, float radius) -> {
			counts[1] += 1;
		};
		
		EntityCollection collection = new EntityCollection(players, creatures);
		collection.findIntersections(ENV, new EntityLocation(0.0f, 0.0f, 0.0f), 3.0f, entityCounter, creatureCounter);
		Assert.assertArrayEquals(new int[] {2, 2}, counts);
		
		counts[0] = 0;
		counts[1] = 0;
		collection.findIntersections(ENV, new EntityLocation(0.0f, 0.0f, 0.0f), 0.5f, entityCounter, creatureCounter);
		Assert.assertArrayEquals(new int[] {0, 0}, counts);
		
		counts[0] = 0;
		counts[1] = 0;
		collection.findIntersections(ENV, new EntityLocation(1.1f, -1.0f, 1.1f), 0.3f, entityCounter, creatureCounter);
		Assert.assertArrayEquals(new int[] {1, 0}, counts);
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

	private static MutableCreature _buildMutableCow(int id, EntityLocation location)
	{
		CreatureEntity cow = _buildCreature(id, location);
		return MutableCreature.existing(cow);
	}
}
