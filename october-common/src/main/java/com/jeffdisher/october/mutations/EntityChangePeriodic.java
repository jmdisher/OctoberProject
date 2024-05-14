package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change is internally scheduled when the entity is first created and reschedules itself whenever it runs.  It is
 * responsible for periodic (every n ticks) entity state changes:
 * -healing
 * -digesting food
 */
public class EntityChangePeriodic implements IMutationEntity<IMutablePlayerEntity>
{
	public static final MutationEntityType TYPE = MutationEntityType.PERIODIC;

	// For now, just say that we run this every second.
	public static final long MILLIS_BETWEEN_PERIODIC_UPDATES = 1000L;
	public static final byte FOOD_HEAL_THRESHOLD = 80;

	// Energy constants.
	public static final int ENERGY_PER_FOOD = 1000;
	// Means we will lose a food every 10 seconds.
	public static final int ENERGY_COST_IDLE = 100;

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
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		int damageToApply = _useEnergy(newEntity, ENERGY_COST_IDLE);
		if (damageToApply > 0)
		{
			// We apply damage using the TakeDamage change.
			// The damage isn't applied to a specific body part.
			EntityChangeTakeDamage<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamage<>(null, (byte)damageToApply);
			context.newChangeSink.next(newEntity.getId(), takeDamage);
		}
		
		// Reschedule, always.
		context.newChangeSink.future(newEntity.getId(), this, MILLIS_BETWEEN_PERIODIC_UPDATES);
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

	@Override
	public boolean canSaveToDisk()
	{
		// This MUST be saved since it reschedules itself.
		return true;
	}


	private static int _useEnergy(IMutablePlayerEntity newEntity, int energy)
	{
		int deficit = newEntity.getEnergyDeficit() + energy;
		byte foodToConsume = 0;
		if (deficit >= ENERGY_PER_FOOD)
		{
			deficit -= ENERGY_PER_FOOD;
			foodToConsume = 1;
		}
		newEntity.setEnergyDeficit(deficit);
		
		// We apply some basic logic:
		// -if food is >=80 and health is <100, increase health
		// -if food is ==0, decrease health
		// -if food is >0, decrease food
		byte food = newEntity.getFood();
		byte health = newEntity.getHealth();
		if ((food >= FOOD_HEAL_THRESHOLD) && (health < 100))
		{
			health += 1;
			foodToConsume += 1;
			newEntity.setHealth(health);
		}
		
		int damageToApply;
		if (food > 0)
		{
			food -= foodToConsume;
			if (food < 0)
			{
				food = 0;
			}
			newEntity.setFood(food);
			damageToApply = 0;
		}
		else
		{
			damageToApply = 1;
		}
		return damageToApply;
	}
}
