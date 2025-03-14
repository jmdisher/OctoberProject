package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.types.EventRecord;
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
	// Means we will lose a food every 20 seconds idling.
	public static final int ENERGY_COST_IDLE = 50;
	// We will assume that attacking takes an entire 2 food.
	public static final int ENERGY_COST_ATTACK = 2 * ENERGY_PER_FOOD;
	// Jumping is pretty expensive.
	public static final int ENERGY_COST_JUMP = 300;
	// Swimming is similar to jumping.
	public static final int ENERGY_COST_SWIM = 200;
	// Crafting is somewhat cheap but depends on time so we will measure this per-second.
	public static final int ENERGY_COST_CRAFT_PER_SECOND = 100;
	// Movement depends on horizontal movement but this is the cost for a single block.
	public static final int ENERGY_COST_MOVE_PER_BLOCK = 100;

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
		newEntity.applyEnergyCost(ENERGY_COST_IDLE);
		_accountForEnergy(context, newEntity);
		
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

	@Override
	public String toString()
	{
		return "Periodic";
	}


	private static void _accountForEnergy(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		int deficit = newEntity.getEnergyDeficit();
		byte foodToConsume = 0;
		if (deficit >= ENERGY_PER_FOOD)
		{
			foodToConsume = (byte)(deficit / ENERGY_PER_FOOD);
			newEntity.setEnergyDeficit(deficit % ENERGY_PER_FOOD);
		}
		
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
		
		if (food > 0)
		{
			food -= foodToConsume;
			if (food < 0)
			{
				food = 0;
			}
			newEntity.setFood(food);
		}
		else
		{
			// They are starving so we want to apply damage.
			// The damage isn't applied to a specific body part.
			EntityChangeTakeDamageFromOther.applyDamageDirectlyAndPostEvent(context, newEntity, MiscConstants.STARVATION_DAMAGE_PER_SECOND, EventRecord.Cause.STARVATION);
		}
	}
}
