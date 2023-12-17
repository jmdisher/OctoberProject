package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change moves an entity in the world.
 */
public class EntityChangeMove implements IEntityChange
{
	private final EntityLocation _oldLocation;
	private final EntityLocation _newLocation;

	public EntityChangeMove(EntityLocation oldLocation, EntityLocation newLocation)
	{
		_oldLocation = oldLocation;
		_newLocation = newLocation;
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
