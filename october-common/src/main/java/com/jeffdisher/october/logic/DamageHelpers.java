package com.jeffdisher.october.logic;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * A container for helpers related to applying damage to an entity.
 */
public class DamageHelpers
{
	/**
	 * Applies damage directly to the given newEntity.  Internally, this will either reduce their health or kill them,
	 * emitting the corresponding event.
	 * This is intended to be applied at the end of tick when processing environment damage factors applied to the
	 * entity.
	 * 
	 * @param context The current tick context.
	 * @param newEntity The entity to modify.
	 * @param damageToApply The damage to apply (must be > 0).
	 * @param cause The cause of the damage when emiting the event for the damage.
	 */
	public static void applyDamageDirectlyAndPostEvent(TickProcessingContext context, IMutableMinimalEntity newEntity, byte damageToApply, EventRecord.Cause cause)
	{
		int finalHealth = newEntity.getHealth() - damageToApply;
		if (finalHealth < 0)
		{
			finalHealth = 0;
		}
		AbsoluteLocation entityLocation = newEntity.getLocation().getBlockLocation();
		EventRecord.Type type;
		if (finalHealth > 0)
		{
			// We can apply the damage.
			newEntity.setHealth((byte)finalHealth);
			type = EventRecord.Type.ENTITY_HURT;
		}
		else
		{
			// The entity is dead so use the type-specific death logic.
			newEntity.handleEntityDeath(context);
			type = EventRecord.Type.ENTITY_KILLED;
		}
		
		context.eventSink.post(new EventRecord(type
				, cause
				, entityLocation
				, newEntity.getId()
				, 0
		));
	}

}
