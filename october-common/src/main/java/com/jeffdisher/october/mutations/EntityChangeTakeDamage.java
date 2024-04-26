package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.SpatialHelpers;
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
		byte damage = buffer.get();
		return new EntityChangeTakeDamage(damage);
	}


	private final byte _damage;

	public EntityChangeTakeDamage(byte damage)
	{
		// Make sure that this is positive.
		Assert.assertTrue(damage > 0);
		
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
			byte finalHealth = (byte)(newEntity.newHealth - _damage);
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
		buffer.put(_damage);
	}
}
