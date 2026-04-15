package com.jeffdisher.october.transactions;

import java.nio.ByteBuffer;
import java.util.Collection;

import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.mutations.IMutationBlock;
import com.jeffdisher.october.mutations.MutationBlockType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


public class MutationBlockTransactionWrapper implements IMutationBlock
{
	private final IMutationBlock _thisMutation;
	private final Collection<AbsoluteLocation> _locationsInTransaction;

	public MutationBlockTransactionWrapper(IMutationBlock thisMutation
		, Collection<AbsoluteLocation> locationsInTransaction
	)
	{
		_thisMutation = thisMutation;
		_locationsInTransaction = locationsInTransaction;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _thisMutation.getAbsoluteLocation();
	}

	@Override
	public void applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// We know that all of these components have been scheduled for the same tick so we should see "1" as the max (note it returns -1 if any are unloaded so we check exactly).
		if (context.transactions.checkScheduledMutationCount(_locationsInTransaction, 1))
		{
			_thisMutation.applyMutation(context, newBlock);
		}
	}

	@Override
	public MutationBlockType getType()
	{
		// Not serialized so we don't need a type.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Not serialized.
		throw Assert.unreachable();
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Transactions cannot be saved to disk.
		return false;
	}
}
