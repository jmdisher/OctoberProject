package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeTakeDamageFromOther<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DEPRECATED_TAKE_DAMAGE_FROM_OTHER_V4;
	public static final byte CAUSE_STARVATION = 1;
	public static final byte CAUSE_SUFFOCATION = 2;
	public static final byte CAUSE_FALL = 3;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeTakeDamageFromOther<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		int damage = buffer.getInt();
		byte cause = buffer.get();
		return new Deprecated_EntityChangeTakeDamageFromOther<>(target, damage, cause);
	}


	private final BodyPart _target;
	private final int _damage;
	private final byte _cause;

	@Deprecated
	public Deprecated_EntityChangeTakeDamageFromOther(BodyPart target, int damage, byte cause)
	{
		Assert.assertTrue(damage > 0);
		
		_target = target;
		_damage = damage;
		_cause = cause;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// This is deprecated so just do nothing (only exists to read old data).
		return true;
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
}
