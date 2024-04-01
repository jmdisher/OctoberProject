package com.jeffdisher.october.logic;

import java.util.List;
import java.util.function.Function;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;


/**
 * Generally a higher-level way of interacting with the PathFinder.  It adds some higher-level logic over the general
 * algorithm and manages updates to the Entity data structure.
 * The class cannot be instantiated since it is just intended to be a container of helpers and may be removed if those
 * move to more appropriate places.
 */
public class EntityActionValidator
{
	private EntityActionValidator()
	{
		// No instantiation.
	}

	public static Entity moveEntity(Function<AbsoluteLocation, Short> blockTypeReader, Entity entity, EntityLocation newLocation, int ticksToMove)
	{
		// First, see how many blocks they could possibly move in this time.
		float maxDistance = ticksToMove * entity.blocksPerTickSpeed();
		// Check the path-finder to see if a path exists.
		List<AbsoluteLocation> path = PathFinder.findPathWithLimit(blockTypeReader, entity.volume(), entity.location(), newLocation, maxDistance);
		// NOTE:  We currently ignore collision with other entities, but that may change in the future.
		// TODO:  We may need to do additional checking or synthesize mutations for things like pressure-plates in the future.
		return (null != path)
				? new Entity(entity.id()
						, newLocation
						, entity.zVelocityPerSecond()
						, entity.volume()
						, entity.blocksPerTickSpeed()
						, entity.inventory()
						, entity.selectedItem()
						, entity.localCraftOperation()
						, entity.health()
						, entity.food()
				)
				: null
		;
	}
}
