package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to an entity, potentially reseting them to world spawn and dropping their inventory.
 * This variant is based on environmental causes, not specific to an entity.
 */
public class EntityChangeTakeDamageFromOther<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.TAKE_DAMAGE_FROM_OTHER;
	public static final byte CAUSE_STARVATION = 1;
	public static final byte CAUSE_SUFFOCATION = 2;
	public static final byte CAUSE_FALL = 3;

	public static <T extends IMutableMinimalEntity> EntityChangeTakeDamageFromOther<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		int damage = buffer.getInt();
		byte cause = buffer.get();
		return new EntityChangeTakeDamageFromOther<>(target, damage, cause);
	}

	/**
	 * Applies damage directly to the given newEntity.  Internally, this will either reduce their health or kill them,
	 * emitting the corresponding event.
	 * This is intended to be applied at the end of tick when processing environment damage factors applied to the
	 * entity.
	 * 
	 * @param context The current tick context.
	 * @param newEntity The entity to modify.
	 * @param damageToApply The damage to apply (must be > 0).
	 * @param cause The cause of the damage when emiting the event for the damage.
	 */
	public static void applyDamageDirectlyAndPostEvent(TickProcessingContext context, IMutableMinimalEntity newEntity, byte damageToApply, EventRecord.Cause cause)
	{
		Assert.assertTrue(damageToApply > 0);
		_applyDamageDirectlyAndPostEvent(context, newEntity, damageToApply, cause);
	}


	private final BodyPart _target;
	private final int _damage;
	private final byte _cause;

	public EntityChangeTakeDamageFromOther(BodyPart target, int damage, byte cause)
	{
		Assert.assertTrue(damage > 0);
		
		_target = target;
		_damage = damage;
		_cause = cause;
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
		if ((health > 0) && newEntity.updateDamageTimeoutIfValid(context.currentTickTimeMillis))
		{
			// Determine how much actual damage to apply by looking at target and armour.
			int damageToApply = CommonEntityMutationHelpers.damageToApplyAfterArmour(newEntity, _target, _damage);
			EventRecord.Cause cause;
			switch (_cause)
			{
			case CAUSE_STARVATION:
				cause = EventRecord.Cause.STARVATION;
				break;
			case CAUSE_SUFFOCATION:
				cause = EventRecord.Cause.SUFFOCATION;
				break;
			case CAUSE_FALL:
				cause = EventRecord.Cause.FALL;
				break;
			default:
				// This is an undefined type.
				throw Assert.unreachable();
			}
			_applyDamageDirectlyAndPostEvent(context, newEntity, (byte)damageToApply, cause);
			
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
		buffer.putInt(_damage);
		buffer.put(_cause);
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
		return "Take " + _damage + " damage to " + _target + " because " + _cause;
	}


	private static void _applyDamageDirectlyAndPostEvent(TickProcessingContext context, IMutableMinimalEntity newEntity, byte damageToApply, EventRecord.Cause cause) throws AssertionError
	{
		int finalHealth = newEntity.getHealth() - damageToApply;
		if (finalHealth < 0)
		{
			finalHealth = 0;
		}
		AbsoluteLocation entityLocation = newEntity.getLocation().getBlockLocation();
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
				, cause
				, entityLocation
				, newEntity.getId()
				, 0
		));
	}
}
