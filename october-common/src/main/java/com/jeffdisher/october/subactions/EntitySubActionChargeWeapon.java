package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Increases the current weapon charge (generally bow draw strength).
 */
public class EntitySubActionChargeWeapon implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.CHARGE_WEAPON;

	/**
	 * The energy cost to charge a weapon, per tick.
	 */
	public static final int ENERGY_COST_PER_TICK_CHARGING_WEAPON = 50;

	public static EntitySubActionChargeWeapon deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntitySubActionChargeWeapon();
	}


	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// See if our currently-selected item is a charge weapon and if we have ammunition for it.
		int key = newEntity.getSelectedKey();
		NonStackableItem nonStack = (0 != key)
			? newEntity.accessMutableInventory().getNonStackableForKey(key)
			: null
		;
		int maxCharge = (null != nonStack)
			? env.tools.getChargeMillis(nonStack.type())
			: 0
		;
		Item ammo = (maxCharge > 0)
			? env.tools.getAmmunitionType(nonStack.type())
			: null
		;
		
		if (null != ammo)
		{
			IMutableInventory mutableInventory = newEntity.accessMutableInventory();
			int count = mutableInventory.getCount(ammo);
			
			if (count > 0)
			{
				// This is a tool which requires charge.
				int currentCharge = newEntity.getCurrentChargeMillis();
				int newCharge = (int)Math.min(currentCharge + context.millisPerTick, maxCharge);
				newEntity.setCurrentChargeMillis(newCharge);
				
				// Charging a weapon expends energy.
				newEntity.applyEnergyCost(ENERGY_COST_PER_TICK_CHARGING_WEAPON);
				
				// Even if we saturated to maximum, we still apply this as valid.
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
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
		// Default case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Charge current weapon";
	}
}
