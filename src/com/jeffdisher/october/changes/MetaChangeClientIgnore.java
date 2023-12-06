package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over locally-originated changes used in the SpeculativeProjection which ignores any attempt to schedule a
 * follow-up phase2 operation when replaying them on top of new authoritative changes.
 * This is used in the cases where we know that we didn't honour the result and didn't store any information related to
 * it.
 */
public class MetaChangeClientIgnore implements IEntityChange
{
	private final IEntityChange _inner;

	public MetaChangeClientIgnore(IEntityChange inner)
	{
		_inner = inner;
	}

	@Override
	public int getTargetId()
	{
		return _inner.getTargetId();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		TickProcessingContext.ITwoPhaseChangeSink ignorePhase2Requests = (int targetEntityId, IEntityChange change, long delayMillis) -> {};
		TickProcessingContext wrapper = new TickProcessingContext(context.currentTick
				, context.previousBlockLookUp
				, context.newMutationSink
				, context.newChangeSink
				, ignorePhase2Requests
		);
		return _inner.applyChange(wrapper, newEntity);
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}
}
