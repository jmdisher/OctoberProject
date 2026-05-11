package com.jeffdisher.october.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * An implementation of the entity search interface.
 */
public class LazyEntityIndex implements TickProcessingContext.IEntitySearch
{
	private final Map<Integer, Entity> _allPlayers;
	private final Map<Integer, CreatureEntity> _allCreatures;
	private SpatialIndex[] _spatialIndexByType;

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

	@Override
	public int[] findEntityIdsInRegion(EntityLocation base, EntityLocation edge)
	{
		// The _spatialIndexByType is lazily constructed since it is rarely used.
		// NOTE:  Index 0 is always empty.
		if (null == _spatialIndexByType)
		{
			// Each index has its own volume so we create each independently.
			Environment env = Environment.getShared();
			
			SpatialIndex.Builder[] builders = new SpatialIndex.Builder[env.creatures.ENTITY_BY_NUMBER.length];
			for (int i = 1; i < builders.length; ++i)
			{
				builders[i] = new SpatialIndex.Builder();
			}
			// Player entity is always type 1.
			Assert.assertTrue(1 == env.creatures.PLAYER.number());
			for (Entity entity : _allPlayers.values())
			{
				builders[1].add(entity.id(), entity.location());
			}
			for (CreatureEntity creature : _allCreatures.values())
			{
				byte number = creature.type().number();
				builders[number].add(creature.id(), creature.location());
			}
			
			_spatialIndexByType = new SpatialIndex[env.creatures.ENTITY_BY_NUMBER.length];
			for (int i = 1; i < _spatialIndexByType.length; ++i)
			{
				_spatialIndexByType[i] = builders[i].finish(env.creatures.ENTITY_BY_NUMBER[i].volume());
			}
		}
		
		List<Integer> ids = new ArrayList<>();
		for (int i = 1; i < _spatialIndexByType.length; ++i)
		{
			Set<Integer> typeIds = _spatialIndexByType[i].idsIntersectingRegion(base, edge);
			ids.addAll(typeIds);
		}
		int[] reduced = new int[ids.size()];
		int index = 0;
		for (Integer id : ids)
		{
			reduced[index] = id;
			index += 1;
		}
		return reduced;
	}
}
