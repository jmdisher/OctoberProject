package com.jeffdisher.october.logic;

import java.util.Collection;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;


public class TestEntityCollection
{
	@Test
	public void checkEmpty()
	{
		Collection<Entity> players = Set.of();
		Collection<CreatureEntity> creatures = Set.of();
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		collection.walkPlayersInRange(centre, 1.0f, (Entity player) -> Assert.fail());
		collection.walkCreaturesInRange(centre, 1.0f, (CreatureEntity creature) -> Assert.fail());
	}

	@Test
	public void noneInRange()
	{
		Collection<Entity> players = Set.of(_buildPlayer(1, new EntityLocation(1.0f, 1.0f, 1.0f)));
		Collection<CreatureEntity> creatures = Set.of(_buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f)));
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		collection.walkPlayersInRange(centre, 1.0f, (Entity player) -> Assert.fail());
		collection.walkCreaturesInRange(centre, 1.0f, (CreatureEntity creature) -> Assert.fail());
	}

	@Test
	public void someInRange()
	{
		Collection<Entity> players = Set.of(_buildPlayer(1, new EntityLocation(1.0f, -1.0f, 1.0f))
				, _buildPlayer(2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		Collection<CreatureEntity> creatures = Set.of(_buildCreature(-1, new EntityLocation(-1.0f, -1.0f, 1.0f))
				, _buildCreature(-2, new EntityLocation(-1.0f, 1.0f, 1.0f))
		);
		EntityCollection collection = new EntityCollection(players, creatures);
		EntityLocation centre = new EntityLocation(0.0f, 0.0f, 0.0f);
		int[] counts = new int[2];
		collection.walkPlayersInRange(centre, 3.0f, (Entity player) -> counts[0] += 1);
		collection.walkCreaturesInRange(centre, 3.0f, (CreatureEntity creature) -> counts[1] += 1);
		
		Assert.assertEquals(2, counts[0]);
		Assert.assertEquals(2, counts[1]);
	}


	private static Entity _buildPlayer(int id, EntityLocation location)
	{
		return new Entity(id
				, location
				, 0.0f
				, 0.0f
				, null
				, null
				, 0
				, null
				, null
				, (byte)0
				, (byte)0
				, 0
		);
	}

	private static CreatureEntity _buildCreature(int id, EntityLocation location)
	{
		return CreatureEntity.create(id, EntityType.COW, location, (byte)10);
	}
}
