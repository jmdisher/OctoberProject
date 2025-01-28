package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains entities (both players and creatures), exposing some high-level interfaces for looking them up.
 */
public class EntityCollection
{
	private final Map<Integer, Entity> _players;
	private final Map<Integer, CreatureEntity> _creatures;

	public EntityCollection(Map<Integer, Entity> players, Map<Integer, CreatureEntity> creatures)
	{
		// TODO:  Build useful indexes once we have a good sense of the performance profile of how this is used.
		_players = players;
		_creatures = creatures;
	}

	/**
	 * Walks all players within maxRange of centre, sending them to consumer and returning how many were found.
	 * 
	 * @param centre The centre of the search.
	 * @param maxRange The maximum range of the search.
	 * @param consumer The consumer for Entity objects within range.
	 * @return The total number of player Entities found.
	 */
	public int walkPlayersInRange(EntityLocation centre, float maxRange, Consumer<Entity> consumer)
	{
		int found = 0;
		for (Entity player : _players.values())
		{
			boolean isInRange = _checkInstance(centre, player.location(), maxRange, consumer, player);
			if (isInRange)
			{
				found += 1;
			}
		}
		return found;
	}

	/**
	 * Walks all creatures within maxRange of centre, sending them to consumer and returning how many were found.
	 * 
	 * @param centre The centre of the search.
	 * @param maxRange The maximum range of the search.
	 * @param consumer The consumer for CreatureEntity objects within range.
	 * @return The total number of CreatureEntities found.
	 */
	public int walkCreaturesInRange(EntityLocation centre, float maxRange, Consumer<CreatureEntity> consumer)
	{
		int found = 0;
		for (CreatureEntity creature : _creatures.values())
		{
			boolean isInRange = _checkInstance(centre, creature.location(), maxRange, consumer, creature);
			if (isInRange)
			{
				found += 1;
			}
		}
		return found;
	}

	/**
	 * Gets a player Entity object by id, returning null if not found.
	 * NOTE:  The ID must be > 0.
	 * 
	 * @param id The player ID (must be > 0).
	 * @return The Entity or null if not found.
	 */
	public Entity getPlayerById(int id)
	{
		Assert.assertTrue(id > 0);
		return _players.get(id);
	}

	/**
	 * Gets a CreatureEntity object by id, returning null if not found.
	 * NOTE:  The ID must be < 0.
	 * 
	 * @param id The creature ID (must be < 0).
	 * @return The CreatureEntity or null if not found.
	 */
	public CreatureEntity getCreatureById(int id)
	{
		Assert.assertTrue(id < 0);
		return _creatures.get(id);
	}


	private static <T> boolean _checkInstance(EntityLocation centre, EntityLocation location, float maxRange, Consumer<T> consumer, T target)
	{
		boolean isInRange = false;
		if ((SpatialHelpers.distanceBetween(centre, location)) <= maxRange)
		{
			consumer.accept(target);
			isInRange = true;
		}
		return isInRange;
	}
}
