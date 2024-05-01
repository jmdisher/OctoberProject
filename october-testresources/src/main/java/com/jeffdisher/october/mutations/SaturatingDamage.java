package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A test mutation which just applies a damage value to the given block if a mutation of this type hasn't been run on
 * this block within this tick.
 */
public class SaturatingDamage implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final short _damage;

	public SaturatingDamage(AbsoluteLocation location, short damage)
	{
		_location = location;
		_damage = damage;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		// We will only apply this if we haven't yet in this tick.
		if (null == newBlock.getEphemeralState())
		{
			newBlock.setDamage((short)(newBlock.getDamage() + _damage));
			// We just need to store ANY object.
			newBlock.setEphemeralState(Boolean.TRUE);
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
