package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.actions.EntityActionTakeDamageFromEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.NudgeHelpers;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FixedRegion;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.MutableCreature;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Issues EntityChangeTakeDamage to the given entity and it will determine if the damage source was in range.  This may
 * change to check the range on the sender side (here), in the future.
 * In the future, we will need this to have some time cost but this is just to get the first step working.
 */
public class EntitySubActionAttackEntity implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.ATTACK_ENTITY;
	public static final long ATTACK_COOLDOWN_MILLIS = 500L;

	public static EntitySubActionAttackEntity deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		int targetEntityId = buffer.getInt();
		return new EntitySubActionAttackEntity(targetEntityId);
	}


	private final int _targetEntityId;

	public EntitySubActionAttackEntity(int targetEntityId)
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
		MinimalEntity targetEntity = context.previousEntityLookUp.getById(_targetEntityId);
		if (isReady && (null != targetEntity))
		{
			// Find the distance from the eye to the target.
			EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(newEntity);
			FixedRegion region = FixedRegion.fromMinimal(targetEntity);
			float distance = SpatialHelpers.distanceFromLocationToRegion(sourceEyeLocation, region);
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
			Environment env = Environment.getShared();
			IMutableInventory mutableInventory = newEntity.accessMutableInventory();
			int selectedKey = newEntity.getSelectedKey();
			ItemSlot slot = mutableInventory.getSlotForKey(selectedKey);
			NonStackableItem nonStack = (null != slot)
				? slot.nonStackable
				: null
			;
			
			// Stackable or empty hand default to 1 damage.
			int damageToApply = 1;
			if (null != nonStack)
			{
				damageToApply = PropertyHelpers.getWeaponMeleeDamage(env, nonStack);
			}
			Assert.assertTrue(damageToApply <= Byte.MAX_VALUE);
			
			// Choose the target body part at random (client doesn't have random so just pick something - the server will re-roll this when it runs it).
			int index = (null != context.randomInt)
				? context.randomInt.applyAsInt(BodyPart.values().length)
				: 0
			;
			BodyPart target = BodyPart.values()[index];
			int sourceEntityId = newEntity.getId();
			if (_targetEntityId > 0)
			{
				EntityActionTakeDamageFromEntity<IMutablePlayerEntity> takeDamage = new EntityActionTakeDamageFromEntity<>(target, damageToApply, sourceEntityId);
				context.newChangeSink.next(_targetEntityId, takeDamage);
			}
			else
			{
				EntityActionTakeDamageFromEntity<MutableCreature> takeDamage = new EntityActionTakeDamageFromEntity<>(target, damageToApply, sourceEntityId);
				context.newChangeSink.creature(_targetEntityId, takeDamage);
			}
			NudgeHelpers.nudgeFromMelee(context
				, _targetEntityId
				, newEntity.getLocation()
				, newEntity.getType().volume()
				, targetEntity.location()
				, targetEntity.type().volume()
			);
			
			CommonEntitySubActionHelpers.decrementToolDurability(env, context, newEntity, mutableInventory, selectedKey, nonStack);
			
			// Attacking expends a lot of energy.
			newEntity.applyEnergyCost(EntityActionPeriodic.ENERGY_COST_PER_ATTACK);
			
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
