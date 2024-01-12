package com.jeffdisher.october.changes;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This change moves an entity in the world.
 */
public class EntityChangeMove implements IEntityChange
{
	/**
	 * The flat distance that an entity can move in a single second.
	 * NOTE:  We currently operate using just axis-aligned movement, so no diagonals.
	 */
	public static final float ENTITY_MOVE_FLAT_LIMIT_PER_SECOND = 4.0f;
	public static final float ENTITY_MOVE_CLIMB_LIMIT_PER_SECOND = 2.0f;
	public static final float ENTITY_MOVE_FALL_LIMIT_PER_SECOND = 20.0f;
	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	/**
	 * Checks that this kind of move can be requested, given the time limits imposed by the change object.
	 * NOTE:  This doesn't mean that the move will succeed (could be a barrier, etc), just that it is well-formed.
	 * 
	 * @return True if this is an acceptable move.
	 */
	public static boolean isValidDistance(long millisBeforeMovement, float xDistance, float yDistance)
	{
		return _isValidDistance(millisBeforeMovement, xDistance, yDistance);
	}

	/**
	 * Calculates the number of milliseconds it will take to move the given distance.
	 * 
	 * @param xDistance The distance in x-axis.
	 * @param yDistance The distance in y-axis.
	 * @return The amount of time, in milliseconds.
	 */
	public static long getTimeMostMillis(float xDistance, float yDistance)
	{
		return _getTimeMostMillis(xDistance, yDistance);
	}


	private final EntityLocation _oldLocation;
	private final long _millisBeforeMovement;
	private final float _xDistance;
	private final float _yDistance;

	public EntityChangeMove(EntityLocation oldLocation, long millisBeforeMovement, float xDistance, float yDistance)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(_isValidDistance(millisBeforeMovement, xDistance, yDistance));
		
		_oldLocation = oldLocation;
		_millisBeforeMovement = millisBeforeMovement;
		_xDistance = xDistance;
		_yDistance = yDistance;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisBeforeMovement + _getTimeMostMillis(_xDistance, _yDistance);
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		boolean oldDoesMatch = _oldLocation.equals(newEntity.newLocation);
		if (oldDoesMatch)
		{
			// Check that they can stand in the target location.
			EntityLocation newLocation = new EntityLocation(_oldLocation.x() + _xDistance, _oldLocation.y() + _yDistance, _oldLocation.z());
			EntityVolume volume = newEntity.original.volume();
			if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, newLocation, volume))
			{
				newEntity.newLocation = newLocation;
				didApply = true;
			}
		}
		return didApply;
	}


	private static boolean _isValidDistance(long millisBeforeMovement, float xDistance, float yDistance)
	{
		long costMillis = _getTimeMostMillis(xDistance, yDistance);
		return ((millisBeforeMovement + costMillis) <= LIMIT_COST_MILLIS);
	}

	private static long _getTimeMostMillis(float xDistance, float yDistance)
	{
		// TODO:  Change this when we allow diagonal movement.
		Assert.assertTrue((0.0f == xDistance) || (0.0f == yDistance));
		
		float xy = Math.abs(xDistance) + Math.abs(yDistance);
		float secondsFlat = (xy / ENTITY_MOVE_FLAT_LIMIT_PER_SECOND);
		return (long) (secondsFlat * 1000.0f);
	}
}
