package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change is internally scheduled when the entity is first created and reschedules itself whenever it runs.  It is
 * responsible for periodic (every n ticks) entity state changes:
 * -healing
 * -digesting food
 */
public class EntityChangePeriodic implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.PERIODIC;

	// For now, just say that we run this every second.
	public static final long MILLIS_BETWEEN_PERIODIC_UPDATES = 1000L;
	public static final byte FOOD_HEAL_THRESHOLD = 80;

	public static EntityChangePeriodic deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangePeriodic();
	}


	public EntityChangePeriodic()
	{
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We apply some basic logic:
		// -if food is >=80 and health is <100, increase health
		// -if food is ==0, decrease health
		// -if food is >0, decrease food
		if ((newEntity.newFood >= FOOD_HEAL_THRESHOLD) && (newEntity.newHealth < 100))
		{
			newEntity.newHealth += 1;
		}
		
		if (newEntity.newFood > 0)
		{
			newEntity.newFood -= 1;
		}
		else
		{
			// We apply damage using the TakeDamage change.
			// The damage isn't applied to a specific body part.
			EntityChangeTakeDamage takeDamage = new EntityChangeTakeDamage(null, (byte)1);
			context.newChangeSink.next(newEntity.original.id(), takeDamage);
		}
		
		// Reschedule, always.
		context.newChangeSink.future(newEntity.original.id(), this, MILLIS_BETWEEN_PERIODIC_UPDATES);
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
	}
}
