package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains entities (both players and creatures), exposing some high-level interfaces for looking them up.
 */
public class EntityCollection
{
	/**
	 * A factory method to create an empty EntityCollection, as this is a common case.
	 * 
	 * @return An instance containing no players or creatures.
	 */
	public static EntityCollection emptyCollection()
	{
		return new EntityCollection(Map.of(), Map.of());
	}

	/**
	 * A factory method to create an EntityCollection from maps of players and creatures.
	 * 
	 * @param players Player entities by player ID.
	 * @param creatures Creatures by creature ID.
	 * @return An instance containing these players and creatures.
	 */
	public static EntityCollection fromMaps(Map<Integer, Entity> players, Map<Integer, CreatureEntity> creatures)
	{
		return new EntityCollection(players, creatures);
	}


	private final Map<Integer, Entity> _players;
	private final SpatialIndex _playerIndex;
	private final Map<Integer, CreatureEntity> _creatures;
	private final SpatialIndex[] _creatureIndices;

	private EntityCollection(Map<Integer, Entity> players, Map<Integer, CreatureEntity> creatures)
	{
		_players = players;
		SpatialIndex.Builder builder = new SpatialIndex.Builder();
		for (Map.Entry<Integer, Entity> elt : players.entrySet())
		{
			builder.add(elt.getKey(), elt.getValue().location());
		}
		Environment env = Environment.getShared();
		_playerIndex = builder.finish(env.creatures.PLAYER.volume());
		_creatures = creatures;
		_creatureIndices = new SpatialIndex[env.creatures.ENTITY_BY_NUMBER.length];
		for (int i = 0; i < env.creatures.ENTITY_BY_NUMBER.length; ++i)
		{
			EntityType type = env.creatures.ENTITY_BY_NUMBER[i];
			if (null != type)
			{
				SpatialIndex.Builder creatureBuilder = new SpatialIndex.Builder();
				boolean didAdd = false;
				for (Map.Entry<Integer, CreatureEntity> elt : creatures.entrySet())
				{
					CreatureEntity creature = elt.getValue();
					if (creature.type() == type)
					{
						creatureBuilder.add(elt.getKey(), creature.location());
						didAdd = true;
					}
				}
				if (didAdd)
				{
					_creatureIndices[i] = creatureBuilder.finish(type.volume());
				}
			}
		}
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

	/**
	 * Finds all player entities and creatures which intersect within range of centre, calling the given intersectors
	 * for each one.
	 * 
	 * @param env The environment.
	 * @param centre The centre-point of the sphere to check.
	 * @param range The radius of the sphere to check.
	 * @param entityVisit The intersector for player entities found (can be null).
	 * @param creatureVisit The intersector for creatures found (can be null).
	 */
	public void findIntersections(Environment env, EntityLocation centre, float range, IIntersector<Entity> entityVisit, IIntersector<CreatureEntity> creatureVisit)
	{
		// TODO:  This is VERY inefficient when a large world is loaded so we need to redesign this based on a different way of organizing or indexing the entities and creatures.
		float squareRange = range * range;
		if (null != entityVisit)
		{
			EntityVolume volume = env.creatures.PLAYER.volume();
			float halfWidth = volume.width() / 2.0f;
			float halfHeight = volume.height() / 2.0f;
			// We are summing square radii in 3-space so we need to multiply.
			float squareSums = 3 * squareRange + 2 * halfWidth * halfWidth + halfHeight * halfHeight;
			for (Entity player : _players.values())
			{
				EntityLocation playerLocation = player.location();
				EntityLocation playerCentre = SpatialHelpers.getCentreOfRegion(playerLocation, volume);
				float dx = centre.x() - playerCentre.x();
				float dy = centre.y() - playerCentre.y();
				float dz = centre.z() - playerCentre.z();
				float squareDistance = dx * dx + dy * dy + dz * dz;
				
				if (squareDistance <= squareSums)
				{
					entityVisit.intersect(player, playerCentre, halfWidth);
				}
			}
		}
		if (null != creatureVisit)
		{
			for (CreatureEntity creature : _creatures.values())
			{
				EntityLocation creatureLocation = creature.location();
				EntityVolume volume = creature.type().volume();
				EntityLocation creatureCentre = SpatialHelpers.getCentreOfRegion(creatureLocation, volume);
				float dx = centre.x() - creatureCentre.x();
				float dy = centre.y() - creatureCentre.y();
				float dz = centre.z() - creatureCentre.z();
				float squareDistance = dx * dx + dy * dy + dz * dz;
				
				// We will just use the XY radius.
				float halfWidth = volume.width() / 2.0f;
				float halfHeight = volume.height() / 2.0f;
				// We are summing square radii in 3-space so we need to multiply.
				float squareSums = 3 * squareRange + 2 * halfWidth * halfWidth + halfHeight * halfHeight;
				if (squareDistance <= squareSums)
				{
					creatureVisit.intersect(creature, creatureCentre, halfWidth);
				}
			}
		}
	}

	/**
	 * Finds all player entities and creatures which intersect the region at base of volume size, calling the given
	 * consumers for each one.  This checks intersections by considering the region and all entities and creatures as
	 * axis-aligned bounding boxes.
	 * 
	 * @param env The environment.
	 * @param base The inclusive base of the region to check (west, south, bottom corner).
	 * @param edge The inclusive edge of the region to check (east, north, up corner).
	 * @param entityVisit The consumer for player entities found (can be null).
	 * @param creatureVisit The consumer for creatures found (can be null).
	 */
	public void walkAlignedIntersections(Environment env, EntityLocation base, EntityLocation edge, Consumer<Entity> entityVisit, Consumer<CreatureEntity> creatureVisit)
	{
		// Note that the edge needs to be greater than the base.
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		
		if (null != entityVisit)
		{
			Set<Integer> ids = _playerIndex.idsIntersectingRegion(base, edge);
			for (int id : ids)
			{
				Entity player = _players.get(id);
				entityVisit.accept(player);
			}
		}
		if (null != creatureVisit)
		{
			for (SpatialIndex index : _creatureIndices)
			{
				if (null != index)
				{
					Set<Integer> ids = index.idsIntersectingRegion(base, edge);
					for (int id : ids)
					{
						CreatureEntity creature = _creatures.get(id);
						creatureVisit.accept(creature);
					}
				}
			}
		}
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


	public static interface IIntersector<T>
	{
		void intersect(T data, EntityLocation centre, float radius);
	}
}
