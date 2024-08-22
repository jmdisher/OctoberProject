package com.jeffdisher.october.logic;

import java.util.Collection;
import java.util.function.Consumer;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;


/**
 * Contains entities (both players and creatures), exposing some high-level interfaces for looking them up.
 */
public class EntityCollection
{
	private final Collection<Entity> _players;
	private final Collection<CreatureEntity> _creatures;

	public EntityCollection(Collection<Entity> players, Collection<CreatureEntity> creatures)
	{
		// We want to walk these collections to build our various indexes.
		// TODO:  Build useful indexes once we have a good sense of the performance profile of how this is used.
		_players = players;
		_creatures = creatures;
	}

	public int walkPlayersInRange(EntityLocation centre, float maxRange, Consumer<Entity> consumer)
	{
		int found = 0;
		for (Entity player : _players)
		{
			boolean isInRange = _checkInstance(centre, player.location(), maxRange, consumer, player);
			if (isInRange)
			{
				found += 1;
			}
		}
		return found;
	}

	public int walkCreaturesInRange(EntityLocation centre, float maxRange, Consumer<CreatureEntity> consumer)
	{
		int found = 0;
		for (CreatureEntity creature : _creatures)
		{
			boolean isInRange = _checkInstance(centre, creature.location(), maxRange, consumer, creature);
			if (isInRange)
			{
				found += 1;
			}
		}
		return found;
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
