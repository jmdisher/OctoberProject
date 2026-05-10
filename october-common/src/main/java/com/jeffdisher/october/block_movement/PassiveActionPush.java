package com.jeffdisher.october.block_movement;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Moves the target PassiveEntity by the given pushDistance vector, leaving other aspects of it unchanged.
 */
public class PassiveActionPush implements IPassiveAction
{
	private final EntityLocation _pushDistance;

	public PassiveActionPush(EntityLocation pushDistance)
	{
		_pushDistance = pushDistance;
	}

	@Override
	public PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
	{
		EntityLocation oldLocation = entity.location();
		EntityLocation newLocation = new EntityLocation(oldLocation.x() + _pushDistance.x()
			, oldLocation.y() + _pushDistance.y()
			, oldLocation.z() + _pushDistance.z()
		);
		return new PassiveEntity(entity.id()
			, entity.type()
			, newLocation
			, entity.velocity()
			, entity.extendedData()
			, entity.lastAliveMillis()
		);
	}
}
