package com.jeffdisher.october.mutations;

import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Implicitly added by CrowdProcessor and CreatureProcessor at the very end of a tick to account for things like gravity
 * and applying the velocity vector to location.
 * These are similar to IMutationEntity but cannot be sent by the clients or stored since they have no serialized shape.
 * However, the client will synthesize these in order to account for time passing between frames.
 */
public class EntityEndOfTick
{
	private final long _millisInTick;

	public EntityEndOfTick(long millisInTick)
	{
		_millisInTick = millisInTick;
	}

	public void apply(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = EntityMovementHelpers.allowMovement(context, newEntity, _millisInTick);
		
		if (didApply)
		{
			// Do other state reset now that we are moving.
			newEntity.resetLongRunningOperations();
		}
	}
}
