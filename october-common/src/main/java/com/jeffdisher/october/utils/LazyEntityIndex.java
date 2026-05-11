package com.jeffdisher.october.utils;

import java.util.Map;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * An implementation of the entity search interface.
 */
public class LazyEntityIndex implements TickProcessingContext.IEntitySearch
{
	private final Map<Integer, Entity> _allPlayers;
	private final Map<Integer, CreatureEntity> _allCreatures;

	public LazyEntityIndex(Map<Integer, Entity> allPlayers, Map<Integer, CreatureEntity> allCreatures)
	{
		_allPlayers = allPlayers;
		_allCreatures = allCreatures;
	}

	@Override
	public MinimalEntity getById(int id)
	{
		return (id > 0)
			? MinimalEntity.fromEntity(_allPlayers.get(id))
			: MinimalEntity.fromCreature(_allCreatures.get(id))
		;
	}
}
