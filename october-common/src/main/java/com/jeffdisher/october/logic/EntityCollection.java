package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.MinimalEntity;
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
	 * Counts the number of players which are within a maxRange of the given centre.  Note that this routine only checks
	 * distances between base locations, ignoring bounding boxes.
	 * 
	 * @param centre The centre of the search.
	 * @param maxRange The maximum range of the search.
	 * @return The total number of player Entities found.
	 */
	public int countPlayersInRangeOfBase(EntityLocation centre, float maxRange)
	{
		int found = 0;
		for (Entity player : _players.values())
		{
			EntityLocation playerLocation = player.location();
			float dx = centre.x() - playerLocation.x();
			float dy = centre.y() - playerLocation.y();
			float dz = centre.z() - playerLocation.z();
			float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance <= maxRange)
			{
				found += 1;
			}
		}
		return found;
	}

	/**
	 * Walks all entities within view distance of searchingCreature, sending them to consumer and returning how many
	 * were found.
	 * 
	 * @param creature The creature issuing the search (will search their view distance).
	 * @param consumer The consumer for Entity objects within range.
	 * @return The total number of Entities found.
	 */
	public int walkPlayersInViewDistance(IMutableMinimalEntity searchingCreature, Consumer<Entity> consumer)
	{
		int found = 0;
		float maxRange = searchingCreature.getType().viewDistance();
		for (Entity player : _players.values())
		{
			boolean isInRange = _checkInstance(searchingCreature, MinimalEntity.fromEntity(player), maxRange, consumer, player);
			if (isInRange)
			{
				found += 1;
			}
		}
		return found;
	}

	/**
	 * Walks all creatures within view distance of searchingCreature, sending them to consumer and returning how many
	 * were found.
	 * 
	 * @param creature The creature issuing the search (will search their view distance).
	 * @param consumer The consumer for CreatureEntity objects within range.
	 * @return The total number of CreatureEntities found.
	 */
	public int walkCreaturesInViewDistance(IMutableMinimalEntity searchingCreature, Consumer<CreatureEntity> consumer)
	{
		int found = 0;
		float maxRange = searchingCreature.getType().viewDistance();
		for (CreatureEntity creature : _creatures.values())
		{
			boolean isInRange = _checkInstance(searchingCreature, MinimalEntity.fromCreature(creature), maxRange, consumer, creature);
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


	private static <T> boolean _checkInstance(IMutableMinimalEntity source, MinimalEntity dest, float maxRange, Consumer<T> consumer, T arg)
	{
		boolean isInRange = false;
		if (SpatialHelpers.distanceFromMutableEyeToEntitySurface(source, dest) <= maxRange)
		{
			consumer.accept(arg);
			isInRange = true;
		}
		return isInRange;
	}
}
