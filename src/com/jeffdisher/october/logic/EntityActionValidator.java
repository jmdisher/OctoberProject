package com.jeffdisher.october.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.utils.Assert;


/**
 * Generally a higher-level way of interacting with the PathFinder.  It adds some higher-level logic over the general
 * algorithm and manages updates to the Entity data structure.
 * The implementation assumes that it is only called by one thread at a time.
 */
public class EntityActionValidator
{
	public static final EntityVolume DEFAULT_VOLUME = new EntityVolume(1.8f, 0.5f);
	public static final EntityLocation DEFAULT_LOCATION = new EntityLocation(0.0f, 0.0f, 0.0f);
	public static final float DEFAULT_BLOCKS_PER_TICK_SPEED = 0.5f;

	private final Function<AbsoluteLocation, Short> _blockTypeReader;
	// Note that this set is only used to validate that the lifecycle calls are balanced.
	private final Set<Integer> _presentEntityIds;

	public EntityActionValidator(Function<AbsoluteLocation, Short> blockTypeReader)
	{
		_blockTypeReader = blockTypeReader;
		_presentEntityIds = new HashSet<>();
	}

	public Entity buildNewlyJoinedEntity(int id)
	{
		// Eventually, the EntityManager will need to call out to load data about this entity but, for now, every entity
		// starts with the same default location, when they join.
		Assert.assertTrue(!_presentEntityIds.contains(id));
		_presentEntityIds.add(id);
		return new Entity(id, DEFAULT_LOCATION, DEFAULT_VOLUME, DEFAULT_BLOCKS_PER_TICK_SPEED);
	}

	public void entityLeft(Entity entity)
	{
		// For now, we don't do anything with this beyond validating who is present.
		Assert.assertTrue(_presentEntityIds.contains(entity.id));
		_presentEntityIds.remove(entity.id);
	}

	public Entity moveEntity(Entity entity, EntityLocation newLocation, int ticksToMove)
	{
		// First, see how many blocks they could possibly move in this time.
		int maxDistance = Math.round(ticksToMove * entity.blocksPerTickSpeed);
		// Check the path-finder to see if a path exists.
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(_blockTypeReader, entity.volume, entity.location, newLocation.getBlockLocation(), maxDistance);
		// NOTE:  We currently ignore collision with other entities, but that may change in the future.
		// TODO:  We may need to do additional checking or synthesize mutations for things like pressure-plates in the future.
		return (null != path)
				? new Entity(entity.id, newLocation, entity.volume, entity.blocksPerTickSpeed)
				: null
		;
	}
}
