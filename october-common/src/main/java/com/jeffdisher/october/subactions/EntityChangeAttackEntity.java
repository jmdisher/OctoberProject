package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityChangePeriodic;
import com.jeffdisher.october.actions.EntityChangeTakeDamageFromEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableCreatureEntity;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues EntityChangeTakeDamage to the given entity and it will determine if the damage source was in range.  This may
 * change to check the range on the sender side (here), in the future.
 * In the future, we will need this to have some time cost but this is just to get the first step working.
 */
public class EntityChangeAttackEntity implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.ATTACK_ENTITY;
	public static final long ATTACK_COOLDOWN_MILLIS = 500L;

	public static EntityChangeAttackEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		int targetEntityId = buffer.getInt();
		return new EntityChangeAttackEntity(targetEntityId);
	}


	private final int _targetEntityId;

	public EntityChangeAttackEntity(int targetEntityId)
	{
		// Note that there is no entity 0 (positive are players, negatives are creatures).
		Assert.assertTrue(0 != targetEntityId);
		
		_targetEntityId = targetEntityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// First, we want to make sure that we are not still busy doing something else.
		boolean isReady = ((newEntity.getLastSpecialActionMillis() + ATTACK_COOLDOWN_MILLIS) <= context.currentTickTimeMillis);
		
		// Check that the target is in range.  We will use block breaking distance.
		boolean isInRange;
		MinimalEntity targetEntity = context.previousEntityLookUp.apply(_targetEntityId);
		if (isReady && (null != targetEntity))
		{
			// Find the distance from the eye to the target.
			float distance = SpatialHelpers.distanceFromMutableEyeToEntitySurface(newEntity, targetEntity);
			isInRange = (distance <= MiscConstants.REACH_ENTITY);
		}
		else
		{
			// Not loaded or not ready to strike.
			isInRange = false;
		}
		if (isInRange)
		{
			// We will just use the tool speed modifier of the selected item to figure out the damage.
			// TODO:  Filter this based on some kind of target type so a sword hits harder than a pick-axe.
			IMutableInventory mutableInventory = newEntity.accessMutableInventory();
			NonStackableItem nonStack = mutableInventory.getNonStackableForKey(newEntity.getSelectedKey());
			Item toolType = (null != nonStack)
					? nonStack.type()
					: null
			;
			Environment env = Environment.getShared();
			int damageToApply = env.tools.toolWeaponDamage(toolType);
			Assert.assertTrue(damageToApply <= Byte.MAX_VALUE);
			
			// Choose the target body part at random.
			int index = context.randomInt.applyAsInt(BodyPart.values().length);
			BodyPart target = BodyPart.values()[index];
			int sourceEntityId = newEntity.getId();
			if (_targetEntityId > 0)
			{
				EntityChangeTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, damageToApply, sourceEntityId);
				context.newChangeSink.next(_targetEntityId, takeDamage);
			}
			else
			{
				EntityChangeTakeDamageFromEntity<IMutableCreatureEntity> takeDamage = new EntityChangeTakeDamageFromEntity<>(target, damageToApply, sourceEntityId);
				context.newChangeSink.creature(_targetEntityId, takeDamage);
			}
			
			// If we have a tool with finite durability equipped, apply this amount of time to wear it down.
			if (null != nonStack)
			{
				int totalDurability = env.durability.getDurability(toolType);
				if (totalDurability > 0)
				{
					// No matter what they hit, this counts as one "weapon use".
					int newDurability = nonStack.durability() - 1;
					int selectedKey = newEntity.getSelectedKey();
					if (newDurability > 0)
					{
						// Write this back.
						NonStackableItem updated = new NonStackableItem(toolType, newDurability);
						mutableInventory.replaceNonStackable(selectedKey, updated);
					}
					else
					{
						// Remove this and clear the selection.
						mutableInventory.removeNonStackableItems(selectedKey);
						newEntity.setSelectedKey(Entity.NO_SELECTION);
					}
				}
			}
			
			// Attacking expends a lot of energy.
			newEntity.applyEnergyCost(EntityChangePeriodic.ENERGY_COST_PER_ATTACK);
			
			// Rate-limit us by updating the special action time.
			newEntity.setLastSpecialActionMillis(context.currentTickTimeMillis);
		}
		return isInRange;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_targetEntityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Target entity may have moved so don't save this.
		return false;
	}

	@Override
	public String toString()
	{
		return "Attack entity " + _targetEntityId;
	}
}
