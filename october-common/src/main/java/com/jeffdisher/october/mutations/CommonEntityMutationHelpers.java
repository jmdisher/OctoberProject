package com.jeffdisher.october.mutations;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class CommonEntityMutationHelpers
{
	/**
	 * Accounts for damage being applied to an entity, modifying any armour it passes through before returning the total
	 * damage to apply.
	 * 
	 * @param context The current tick.
	 * @param newEntity The entity taking damage (will be modified).
	 * @param target The body part taking damage (could be null).
	 * @param damage The amount of damage to apply (must be positive).
	 * @return The damage to actually pass to the entity after accounting for armour.
	 */
	public static int damageToApplyAfterArmour(TickProcessingContext context, IMutableMinimalEntity newEntity, BodyPart target, int damage)
	{
		Assert.assertTrue(damage > 0);
	
		int damageToApply;
		if (null != target)
		{
			// This means an actual body part.
			NonStackableItem armour = newEntity.getArmour(target);
			if (null != armour)
			{
				// We will reduce the damage by the armour amount, correcting to make sure at least 1 damage is taken, then apply this to the armour durability.
				Environment env = Environment.getShared();
				// (note that we consider the armour 100% effective, even if about to break).
				int maxReduction = env.armour.getDamageReduction(armour.type());
				int damageToAbsorb = Math.min(damage - 1, maxReduction);
				damageToApply = damage - damageToAbsorb;
				if (damageToAbsorb > 0)
				{
					// This will change to null if broken.
					int randomNumberTo255 = context.randomInt.applyAsInt(256);
					newEntity.setArmour(target, PropertyHelpers.reduceDurabilityOrBreak(armour, damageToAbsorb, randomNumberTo255));
				}
			}
			else
			{
				damageToApply = damage;
			}
		}
		else
		{
			// This means some kind of environmental damage (starvation, usually).
			damageToApply = damage;
		}
		return damageToApply;
	}

}
