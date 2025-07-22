package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.DamageHelpers;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change is internally scheduled when the entity is first created and reschedules itself whenever it runs.  It is
 * responsible for periodic (every n ticks) entity state changes:
 * -healing
 * -digesting food
 */
public class EntityChangePeriodic implements IEntityAction<IMutablePlayerEntity>
{
	public static final EntityActionType TYPE = EntityActionType.PERIODIC;

	/**
	 * How often we run the periodic entity update:  We will run this every second.
	 */
	public static final long MILLIS_BETWEEN_PERIODIC_UPDATES = 1000L;
	/**
	 * The minimum food level the entity can have in order to regenerate health passively.
	 */
	public static final byte FOOD_HEAL_THRESHOLD = 80;

	/**
	 * The energy replenished by reducing the food counter by one.
	 */
	public static final int ENERGY_PER_FOOD = 1000;
	/**
	 * The baseline "idle" cost, in energy, incurred for every periodic update.
	 */
	public static final int ENERGY_COST_PER_PERIODIC = 10;
	/**
	 * The energy cost to attack an entity, once.
	 */
	public static final int ENERGY_COST_PER_ATTACK = 200;
	/**
	 * The energy cost to break/repair a block for one tick.
	 */
	public static final int ENERGY_COST_PER_TICK_BREAK_BLOCK = 100;
	/**
	 * The energy cost to place a block.
	 */
	public static final int ENERGY_COST_PLACE_BLOCK = 50;
	/**
	 * The energy cost to jump, once.
	 */
	public static final int ENERGY_COST_PER_JUMP = 300;
	/**
	 * The energy cost to "swim up", once.
	 */
	public static final int ENERGY_COST_PER_SWIM_UP = 200;
	/**
	 * The energy cost to craft in inventory, per tick.
	 */
	public static final int ENERGY_COST_PER_TICK_INVENTORY_CRAFT = 20;
	/**
	 * The energy cost to craft in a block, per tick.
	 */
	public static final int ENERGY_COST_PER_TICK_BLOCK_CRAFT = 10;
	/**
	 * The energy cost to move with walking intensity, per tick.
	 */
	public static final int ENERGY_COST_PER_TICK_WALKING = 10;
	/**
	 * The energy cost to move with running intensity, per tick.  We want this to be less efficient than walking.
	 */
	public static final int ENERGY_COST_PER_TICK_RUNNING = 30;
	/**
	 * The energy cost to passively regenerate 1 health point.
	 */
	public static final int ENERGY_COST_PER_HEALTH_HEAL = 100;

	public static EntityChangePeriodic deserialize(DeserializationContext context)
	{
		return new EntityChangePeriodic();
	}


	public EntityChangePeriodic()
	{
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		newEntity.applyEnergyCost(ENERGY_COST_PER_PERIODIC);
		_accountForEnergy(context, newEntity);
		
		// Reschedule, always.
		context.newChangeSink.future(newEntity.getId(), this, MILLIS_BETWEEN_PERIODIC_UPDATES);
		return true;
	}

	@Override
	public EntityActionType getType()
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
			DamageHelpers.applyDamageDirectlyAndPostEvent(context, newEntity, MiscConstants.STARVATION_DAMAGE_PER_SECOND, EventRecord.Cause.STARVATION);
		}
	}
}
