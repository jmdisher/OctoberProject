package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to an entity, potentially reseting them to world spawn and dropping their inventory.
 * Note that the damage origin is passed in here and checked for range but that will likely be moved to the sender, in
 * the future.
 */
public class EntityChangeTakeDamage implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.TAKE_DAMAGE;

	public static EntityChangeTakeDamage deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		byte damage = buffer.get();
		return new EntityChangeTakeDamage(target, damage);
	}


	private final BodyPart _target;
	private final byte _damage;

	public EntityChangeTakeDamage(BodyPart target, byte damage)
	{
		// Make sure that this is positive.
		Assert.assertTrue(damage > 0);
		
		_target = target;
		_damage = damage;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Taking damage doesn't use any time.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// We will move the respawn into the next tick so that they don't keep taking damage from within this tick.
		boolean didApply = false;
		if (newEntity.newHealth > 0)
		{
			// Determine how much actual damage to apply by looking at target and armour.
			byte damageToApply;
			if (null != _target)
			{
				// This means an actual body part.
				NonStackableItem armour = newEntity.newArmour[_target.ordinal()];
				if (null != armour)
				{
					// We will reduce the damage by the armour amount, correcting to make sure at least 1 damage is taken, then apply this to the armour durability.
					Environment env = Environment.getShared();
					// (note that we consider the armour 100% effective, even if about to break).
					int maxReduction = env.armour.getDamageReduction(armour.type());
					int damageToAbsorb = Math.min(_damage - 1, maxReduction);
					damageToApply = (byte)(_damage - damageToAbsorb);
					if (damageToAbsorb > 0)
					{
						int durabilityRemaining = armour.durability() - damageToAbsorb;
						if (durabilityRemaining > 0)
						{
							newEntity.newArmour[_target.ordinal()] = new NonStackableItem(armour.type(), durabilityRemaining);
						}
						else
						{
							newEntity.newArmour[_target.ordinal()] = null;
						}
					}
				}
				else
				{
					damageToApply = _damage;
				}
			}
			else
			{
				// This means some kind of environmental damage (starvation, usually).
				damageToApply = _damage;
			}
			byte finalHealth = (byte)(newEntity.newHealth - damageToApply);
			if (finalHealth > 0)
			{
				// We can apply the damage.
				newEntity.newHealth = finalHealth;
			}
			else
			{
				// The entity is dead so "respawn" them by resetting fields and dropping inventory onto the ground.
				EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.newLocation, newEntity.original.volume());
				newEntity.newLocation = MutableEntity.DEFAULT_LOCATION;
				newEntity.newHealth = MutableEntity.DEFAULT_HEALTH;
				newEntity.newFood = MutableEntity.DEFAULT_FOOD;
				for (Integer key : newEntity.newInventory.freeze().sortedKeys())
				{
					Items stackable = newEntity.newInventory.getStackForKey(key);
					NonStackableItem nonStackable = newEntity.newInventory.getNonStackableForKey(key);
					Assert.assertTrue((null != stackable) != (null != nonStackable));
					context.mutationSink.next(new MutationBlockStoreItems(entityCentre.getBlockLocation(), stackable, nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY));
				}
				newEntity.newInventory.clearInventory(null);
				// Wipe all the hotbar slots.
				for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
				{
					newEntity.newHotbar[i] = Entity.NO_SELECTION;
				}
			}
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeBodyPart(buffer, _target);
		buffer.put(_damage);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
