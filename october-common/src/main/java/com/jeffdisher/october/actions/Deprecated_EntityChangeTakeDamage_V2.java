package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class Deprecated_EntityChangeTakeDamage_V2<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.DEPRECATED_TAKE_DAMAGE_V2;

	public static <T extends IMutableMinimalEntity> Deprecated_EntityChangeTakeDamage_V2<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart target = CodecHelpers.readBodyPart(buffer);
		byte damage = buffer.get();
		return new Deprecated_EntityChangeTakeDamage_V2<>(target, damage);
	}


	private final BodyPart _target;
	private final byte _damage;

	@Deprecated
	public Deprecated_EntityChangeTakeDamage_V2(BodyPart target, byte damage)
	{
		// Make sure that this is positive.
		Assert.assertTrue(damage > 0);
		
		_target = target;
		_damage = damage;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		// This is deprecated so just do nothing (only exists to read old data).
		return true;
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
