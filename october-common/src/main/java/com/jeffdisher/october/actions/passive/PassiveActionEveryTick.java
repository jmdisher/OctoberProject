package com.jeffdisher.october.actions.passive;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.subactions.EntitySubActionPopOutOfBlock;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * These are synthesized by the system for every tick and applied to every loaded passive entity.
 * This means that it is responsible for normal movement (which is just passively applying existing velocity and
 * gravity) but also despawn logic.
 */
public class PassiveActionEveryTick implements IPassiveAction
{
	@Override
	public PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
	{
		Environment env = Environment.getShared();
		
		// Currently, we only have the ItemSlot type.
		PassiveType type = entity.type();
		Assert.assertTrue(PassiveType.ITEM_SLOT == type);
		
		// Check if this should despawn or if we should apply movement.
		PassiveEntity result;
		long lastAliveMillis = entity.lastAliveMillis();
		long despawnMillis = lastAliveMillis + PassiveType.ITEM_SLOT_DESPAWN_MILLIS;
		if (context.currentTickTimeMillis >= despawnMillis)
		{
			// We can despawn this.
			result = null;
		}
		else if (DamageHelpers.findEnvironmentalDamageInVolume(env, context.previousBlockLookUp, entity.location(), entity.type().volume()) > 0)
		{
			// Passives immediately despawn upon taking damage.
			result = null;
		}
		else
		{
			// This is still alive so apply movement.
			EntityLocation startLocation = entity.location();
			EntityLocation startVelocity = entity.velocity();
			EntityVolume volume = type.volume();
			
			// Check to see if we need to pop-out of a block.
			EntityLocation popOut = EntitySubActionPopOutOfBlock.popOutLocation(context.previousBlockLookUp, startLocation, volume);
			if (null != popOut)
			{
				startLocation = popOut;
				startVelocity = new EntityLocation(0.0f, 0.0f, 0.0f);
			}
			
			// Now apply normal movement.
			float seconds = (float)context.millisPerTick / EntityMovementHelpers.FLOAT_MILLIS_PER_SECOND;
			EntityMovementHelpers.HighLevelMovementResult movement = EntityMovementHelpers.commonMovementIdiom(context.previousBlockLookUp
				, startLocation
				, startVelocity
				, volume
				, 0.0f
				, 0.0f
				, 0.0f
				, seconds
			);
			EntityLocation finalLocation = movement.location();
			EntityLocation finalVelocity = new EntityLocation(movement.vX(), movement.vY(), movement.vZ());
			
			// We need to determine if this moved.
			if (!finalLocation.equals(entity.location()) || !finalVelocity.equals(entity.velocity()))
			{
				// This changed location so rebuild it.
				result = new PassiveEntity(entity.id()
					, type
					, finalLocation
					, finalVelocity
					, entity.extendedData()
					, lastAliveMillis
				);
			}
			else
			{
				// Unmoved so just retain the instance.
				result = entity;
			}
		}
		return result;
	}
}
