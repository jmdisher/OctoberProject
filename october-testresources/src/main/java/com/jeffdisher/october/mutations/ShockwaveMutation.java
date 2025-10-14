package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class ShockwaveMutation implements IMutationBlock
{
	private final AbsoluteLocation _location;
	private final int _count;

	public ShockwaveMutation(AbsoluteLocation location, int count)
	{
		_location = location;
		_count = count;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// We want to apply a single point of damage to this block, so that we changed something.
		Block block = newBlock.getBlock();
		if (DamageAspect.UNBREAKABLE != env.damage.getToughness(block))
		{
			short newDamage = (short)(newBlock.getDamage() + 1);
			newBlock.setDamage(newDamage);
			didApply = true;
		}
		
		// Now, apply this to the other blocks.
		_commonMutation((IMutationBlock mutation) -> context.mutationSink.next(mutation));
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


	private void _commonMutation(Consumer<IMutationBlock> newMutationSink)
	{
		if (_count > 0)
		{
			int thisCount = _count - 1;
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() - 1), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y(), _location.z() + 1), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() - 1, _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x(), _location.y() + 1, _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() - 1, _location.y(), _location.z()), thisCount));
			newMutationSink.accept(new ShockwaveMutation(new AbsoluteLocation(_location.x() + 1, _location.y(), _location.z()), thisCount));
		}
	}
}
