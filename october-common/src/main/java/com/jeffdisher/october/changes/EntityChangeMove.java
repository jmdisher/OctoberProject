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

	public static boolean isValidMove(EntityLocation oldLocation, EntityLocation newLocation)
	{
		return _isValidMove(oldLocation, newLocation);
	}


	private final EntityLocation _oldLocation;
	private final EntityLocation _newLocation;

	public EntityChangeMove(EntityLocation oldLocation, EntityLocation newLocation)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(_isValidMove(oldLocation, newLocation));
		
		_oldLocation = oldLocation;
		_newLocation = newLocation;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _getTimeMostMillis(_oldLocation, _newLocation);
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		boolean oldDoesMatch = _oldLocation.equals(newEntity.newLocation);
		if (oldDoesMatch)
		{
			// Check that they can stand in the target location.
			EntityVolume volume = newEntity.original.volume();
			if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, _newLocation, volume))
			{
				newEntity.newLocation = _newLocation;
				didApply = true;
			}
		}
		return didApply;
	}


	private static boolean _isValidMove(EntityLocation oldLocation, EntityLocation newLocation)
	{
		long costMillis = _getTimeMostMillis(oldLocation, newLocation);
		return (costMillis <= LIMIT_COST_MILLIS);
	}

	private static long _getTimeMostMillis(EntityLocation oldLocation, EntityLocation newLocation)
	{
		float xy = Math.abs(newLocation.x() - oldLocation.x()) + Math.abs(newLocation.y() - oldLocation.y());
		float zChange = newLocation.z() - oldLocation.z();
		float climb;
		float fall;
		if (zChange > 0.0f)
		{
			climb = zChange;
			fall = 0.0f;
		}
		else
		{
			climb = 0.0f;
			fall = zChange;
		}
		float secondsFlat = (xy / ENTITY_MOVE_FLAT_LIMIT_PER_SECOND);
		float secondsClimb = (climb / ENTITY_MOVE_CLIMB_LIMIT_PER_SECOND);
		float secondsFall = (fall / ENTITY_MOVE_FALL_LIMIT_PER_SECOND);
		float totalSeconds = secondsFlat + secondsClimb + secondsFall;
		return (long) (totalSeconds * 1000.0f);
	}
}
