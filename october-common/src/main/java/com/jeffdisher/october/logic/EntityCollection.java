package com.jeffdisher.october.logic;

import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
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
	private final Map<Integer, CreatureEntity> _creatures;

	private EntityCollection(Map<Integer, Entity> players, Map<Integer, CreatureEntity> creatures)
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
	 * @param base The base of the region to check (west, south, bottom corner).
	 * @param edge The edge of the region to check (east, north, up corner).
	 * @param entityVisit The consumer for player entities found (can be null).
	 * @param creatureVisit The consumer for creatures found (can be null).
	 */
	public void walkAlignedIntersections(Environment env, EntityLocation base, EntityLocation edge, Consumer<Entity> entityVisit, Consumer<CreatureEntity> creatureVisit)
	{
		// Note that the edge needs to be greater than the base.
		Assert.assertTrue(base.x() <= edge.x());
		Assert.assertTrue(base.y() <= edge.y());
		Assert.assertTrue(base.z() <= edge.z());
		
		// TODO:  This is VERY inefficient when a large world is loaded so we need to redesign this based on a different way of organizing or indexing the entities and creatures.
		if (null != entityVisit)
		{
			EntityVolume playerVolume = env.creatures.PLAYER.volume();
			
			for (Entity player : _players.values())
			{
				EntityLocation playerLocation = player.location();
				if (_regionsIntersect(base, edge, playerLocation, playerVolume))
				{
					entityVisit.accept(player);
				}
			}
		}
		if (null != creatureVisit)
		{
			for (CreatureEntity creature : _creatures.values())
			{
				EntityLocation creatureLocation = creature.location();
				EntityVolume creatureVolume = creature.type().volume();
				if (_regionsIntersect(base, edge, creatureLocation, creatureVolume))
				{
					creatureVisit.accept(creature);
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

	private static boolean _regionsIntersect(EntityLocation base, EntityLocation edge, EntityLocation checkLocation, EntityVolume checkVolume)
	{
		float deltaX = base.x() - checkLocation.x();
		boolean intersectX = (deltaX >= 0.0f)
			? (deltaX <= checkVolume.width())
			: (-deltaX <= (edge.x() - base.x()))
		;
		float deltaY = base.y() - checkLocation.y();
		boolean intersectY = (deltaY >= 0.0f)
			? (deltaY <= checkVolume.width())
			: (-deltaY <= (edge.y() - base.y()))
		;
		float deltaZ = base.z() - checkLocation.z();
		boolean intersectZ = (deltaZ >= 0.0f)
			? (deltaZ <= checkVolume.height())
			: (-deltaZ <= (edge.z() - base.z()))
		;
		return intersectX && intersectY && intersectZ;
	}


	public static interface IIntersector<T>
	{
		void intersect(T data, EntityLocation centre, float radius);
	}
}
