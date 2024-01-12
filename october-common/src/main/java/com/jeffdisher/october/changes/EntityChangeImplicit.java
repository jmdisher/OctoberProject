package com.jeffdisher.october.changes;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is a special change implicitly added by the TickRunner and run against all entities at the end of a tick.
 * The reason for this is that we can use it to apply general rules of the environment (falling, starving, etc).
 * Since this is added by the server, internally, it should reject any attempt made by the client to explicitly send it.
 * Additionally, the same instance is reused so it must be stateless.
 */
public class EntityChangeImplicit implements IEntityChange
{
	@Override
	public long getTimeCostMillis()
	{
		// This is never called since these aren't scheduled but implicitly run.
		throw Assert.unreachable();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// If this does anything, we want to say it applied so we will send it to the clients.
		boolean didApply = false;
		
		// Changes to apply:
		// 1) Gravity - could they stand one block below where they currently are.
		// (in the future, we probably want to give them a movement vector so they can have realistic acceleration instead of this constant fall rate)
		EntityLocation oldLocation = newEntity.newLocation;
		EntityLocation newLocation = new EntityLocation(oldLocation.x(), oldLocation.y(), oldLocation.z() - 1.0f);
		boolean canStand = SpatialHelpers.canExistInLocation(context.previousBlockLookUp, newLocation, newEntity.original.volume());
		if (canStand)
		{
			newEntity.newLocation = newLocation;
			didApply = true;
		}
		return didApply;
	}
}
