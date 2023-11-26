package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change moves an entity in the world.
 */
public class EntityChangeMove implements IEntityChange
{
	private final int _id;
	private final EntityLocation _newLocation;

	public EntityChangeMove(int id, EntityLocation newLocation)
	{
		_id = id;
		_newLocation = newLocation;
	}

	@Override
	public int getTargetId()
	{
		return _id;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		newEntity.newLocation = _newLocation;
		// Movement always succeeds.
		return true;
	}

	@Override
	public IEntityChange applyChangeReversible(TickProcessingContext context, MutableEntity newEntity)
	{
		// Just move us back to where we were.
		EntityChangeMove reverse = new EntityChangeMove(_id, newEntity.newLocation);
		newEntity.newLocation = _newLocation;
		return reverse;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// We can replace the previous if it was also a move.
		// This should be something we can merge if the previous was also a move.
		boolean canReplace = false;
		if (previousChange instanceof EntityChangeMove)
		{
			canReplace = true;
		}
		return canReplace;
	}
}
