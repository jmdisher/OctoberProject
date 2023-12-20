package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


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

	private final EntityLocation _oldLocation;
	private final EntityLocation _newLocation;

	public EntityChangeMove(EntityLocation oldLocation, EntityLocation newLocation)
	{
		_oldLocation = oldLocation;
		_newLocation = newLocation;
	}

	@Override
	public long getTimeCostMillis()
	{
		float xy = Math.abs(_newLocation.x() - _oldLocation.x()) + Math.abs(_newLocation.y() - _oldLocation.y());
		float zChange = _newLocation.z() - _oldLocation.z();
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

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean oldDoesMatch = _oldLocation.equals(newEntity.newLocation);
		if (oldDoesMatch)
		{
			newEntity.newLocation = _newLocation;
		}
		return oldDoesMatch;
	}
}
