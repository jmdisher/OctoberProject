package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to an entity, potentially reseting them to world spawn and dropping their inventory.
 * This variant accepts the ID of the entity which applied the damage so it cannot be persisted.
 * Note that the entity may no longer exist when this is processed so care must be taken in resolving it (it could have
 * died in the same tick where it sent this).
 */
public class EntityActionTakeDamageFromEntity<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.TAKE_DAMAGE_FROM_ENTITY;

	public static <T extends IMutableMinimalEntity> EntityActionTakeDamageFromEntity<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		int damage = buffer.getInt();
		int sourceEntityId = buffer.getInt();
		return new EntityActionTakeDamageFromEntity<>(target, damage, sourceEntityId);
	}


	private final BodyPart _target;
	private final int _damage;
	private final int _sourceEntityId;

	public EntityActionTakeDamageFromEntity(BodyPart target, int damage, int sourceEntityId)
	{
		Assert.assertTrue(null != target);
		Assert.assertTrue(damage > 0);
		// Note that sourceEntityId will be valid during melee attacks but things like projectile attacks don't come from a specific entity.
		
		_target = target;
		_damage = damage;
		_sourceEntityId = sourceEntityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// We will move the respawn into the next tick so that they don't keep taking damage from within this tick.
		boolean didApply = false;
		byte health = newEntity.getHealth();
		if ((health > 0) && newEntity.updateDamageTimeoutIfValid(context.currentTickTimeMillis))
		{
			// Determine how much actual damage to apply by looking at target and armour.
			int damageToApply = _damageToApplyAfterArmour(context, newEntity, _target, _damage);
			int finalHealth = health - damageToApply;
			AbsoluteLocation startLocation = newEntity.getLocation().getBlockLocation();
			EventRecord.Type type;
			if (finalHealth > 0)
			{
				// We can apply the damage.
				newEntity.setHealth((byte)finalHealth);
				type = EventRecord.Type.ENTITY_HURT;
			}
			else
			{
				// The entity is dead so use the type-specific death logic.
				newEntity.handleEntityDeath(context);
				type = EventRecord.Type.ENTITY_KILLED;
			}
			
			context.eventSink.post(new EventRecord(type
					, EventRecord.Cause.ATTACKED
					, startLocation
					, newEntity.getId()
					, _sourceEntityId
			));
			
			didApply = true;
		}
		return didApply;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeBodyPart(buffer, _target);
		buffer.putInt(_damage);
		buffer.putInt(_sourceEntityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// No - contains entity reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Take " + _damage + " damage to " + _target + " from " + _sourceEntityId;
	}


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
	private static int _damageToApplyAfterArmour(TickProcessingContext context, IMutableMinimalEntity newEntity, BodyPart target, int damage)
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
