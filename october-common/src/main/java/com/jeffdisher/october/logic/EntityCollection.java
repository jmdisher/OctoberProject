package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.SpatialIndex;


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
	 * Finds all player entities which intersect the region at base of volume size, calling entityVisit for each.  This
	 * checks intersections by considering the region and all entities as axis-aligned bounding boxes.
	 * 
	 * @param base The inclusive base of the region to check (west, south, bottom corner).
	 * @param edge The inclusive edge of the region to check (east, north, up corner).
	 * @param entityVisit The consumer for player entities found (can be null).
	 */
	public void walkAlignedEntityIntersections(EntityLocation base, EntityLocation edge, Consumer<Entity> entityVisit)
	{
		// Note that the edge needs to be greater than the base.
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		Assert.assertTrue(null != entityVisit);
		
		Set<Integer> ids = _playerIndex.idsIntersectingRegion(base, edge);
		for (int id : ids)
		{
			Entity player = _players.get(id);
			entityVisit.accept(player);
		}
	}

	/**
	 * Finds all creatures which intersect the region at base of volume size, calling creatureVisit for each.  This
	 * checks intersections by considering the region and all creatures as axis-aligned bounding boxes.
	 * 
	 * @param base The inclusive base of the region to check (west, south, bottom corner).
	 * @param edge The inclusive edge of the region to check (east, north, up corner).
	 * @param creatureVisit The consumer for creatures found (can be null).
	 */
	public void walkAlignedCreatureIntersections(EntityLocation base, EntityLocation edge, Consumer<CreatureEntity> creatureVisit)
	{
		// Note that the edge needs to be greater than the base.
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		Assert.assertTrue(null != creatureVisit);
		
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

	/**
	 * Finds creatures of type which intersect the region at base of volume size, calling creatureVisit for each.  This
	 * checks intersections by considering the region and all creatures as axis-aligned bounding boxes.
	 * 
	 * @param base The inclusive base of the region to check (west, south, bottom corner).
	 * @param edge The inclusive edge of the region to check (east, north, up corner).
	 * @param type The type of creature to return.
	 * @param creatureVisit The consumer for creatures found (can be null).
	 */
	public void walkAlignedCreatureTypeIntersections(EntityLocation base, EntityLocation edge, EntityType type, Consumer<CreatureEntity> creatureVisit)
	{
		// Note that the edge needs to be greater than the base.
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		Assert.assertTrue(null != type);
		Assert.assertTrue(null != creatureVisit);
		
		SpatialIndex index = _creatureIndices[type.number()];
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

	/**
	 * Finds the closest player entity in view distance of searchingCreature which matches the given shouldConsider
	 * predicate.
	 * 
	 * @param searchingCreature The creature issuing the search (will search their view distance from their eye).
	 * @param shouldConsider The predicate to check which players to consider.
	 * @return The description of the found entity (null if none found or matched the shouldConsider predicate).
	 */
	public EntityType.TargetEntity findClosestPlayerInViewDistance(IMutableMinimalEntity searchingCreature, Predicate<Entity> shouldConsider)
	{
		// Note that we currently walk all the players on the server, which may run into scaling problems in the future.
		// (we could walk _playerIndex if we wanted a square search instead of circular, and if the population became
		// very high - this current design is very low-constant).
		EntityType creatureType = searchingCreature.getType();
		float maxRange = creatureType.viewDistance();
		EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(searchingCreature);
		EntityVolume playerVolume = Environment.getShared().creatures.PLAYER.volume();
		
		EntityType.TargetEntity found = null;
		float matchDistance = Float.POSITIVE_INFINITY;
		for (Entity player : _players.values())
		{
			EntityLocation playerBase = player.location();
			float distance = SpatialHelpers.distanceFromLocationToVolume(sourceEyeLocation, playerBase, playerVolume);
			if ((distance <= maxRange)
				&& (distance < matchDistance)
				&& shouldConsider.test(player)
			)
			{
				found = new EntityType.TargetEntity(player.id(), playerBase);
				matchDistance = distance;
			}
		}
		return found;
	}

	/**
	 * Finds the closest creature in view distance of searchingCreature which has the same type as searchingCreature and
	 * matches the given shouldConsider predicate (and obviously excludes itself).
	 * 
	 * @param searchingCreature The creature issuing the search (will search their view distance from their eye).
	 * @param shouldConsider The predicate to check which players to consider.
	 * @return The description of the found creature (null if none found or matched the shouldConsider predicate).
	 */
	public EntityType.TargetEntity findClosestCreatureOfMatchedTypeInViewDistance(IMutableMinimalEntity searchingCreature, Predicate<CreatureEntity> shouldConsider)
	{
		// Note that we currently walk all the loaded creatures on the server, which may run into scaling problems in
		// the future.
		// (we could walk _creatureIndices for this creature type if we wanted a square search instead of circular, and
		// if the population became very high).
		EntityType creatureType = searchingCreature.getType();
		float maxRange = creatureType.viewDistance();
		EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(searchingCreature);
		int searchingId = searchingCreature.getId();
		
		EntityType.TargetEntity found = null;
		float matchDistance = Float.POSITIVE_INFINITY;
		for (CreatureEntity creature : _creatures.values())
		{
			int creatureId = creature.id();
			if ((searchingId != creatureId) && (creature.type() == creatureType))
			{
				FixedRegion region = FixedRegion.fromCreature(creature);
				float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEyeLocation, region);
				if ((distance <= maxRange)
					&& (distance < matchDistance)
					&& shouldConsider.test(creature)
				)
				{
					EntityLocation targetBase = creature.location();
					found = new EntityType.TargetEntity(creatureId, targetBase);
					matchDistance = distance;
				}
			}
		}
		return found;
	}
}
