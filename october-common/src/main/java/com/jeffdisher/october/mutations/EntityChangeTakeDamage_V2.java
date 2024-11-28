package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A deprecated version of the damage mutation which was last present in V2 of the entity persistent data.
 */
public class EntityChangeTakeDamage_V2<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.OLD_TAKE_DAMAGE_V2;

	public static <T extends IMutableMinimalEntity> EntityChangeTakeDamage_V2<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		byte damage = buffer.get();
		return new EntityChangeTakeDamage_V2<>(target, damage);
	}


	private final BodyPart _target;
	private final byte _damage;

	public EntityChangeTakeDamage_V2(BodyPart target, byte damage)
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
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// We will move the respawn into the next tick so that they don't keep taking damage from within this tick.
		boolean didApply = false;
		byte health = newEntity.getHealth();
		if (health > 0)
		{
			// Determine how much actual damage to apply by looking at target and armour.
			byte damageToApply;
			if (null != _target)
			{
				// This means an actual body part.
				NonStackableItem armour = newEntity.getArmour(_target);
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
							newEntity.setArmour(_target, new NonStackableItem(armour.type(), durabilityRemaining));
						}
						else
						{
							newEntity.setArmour(_target, null);
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
			byte finalHealth = (byte)(health - damageToApply);
			if (finalHealth > 0)
			{
				// We can apply the damage.
				newEntity.setHealth(finalHealth);
			}
			else
			{
				// The entity is dead so "respawn" them by resetting fields and dropping inventory onto the ground.
				newEntity.handleEntityDeath(context);
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

	@Override
	public String toString()
	{
		return "Take " + _damage + " damage to " + _target;
	}
}
