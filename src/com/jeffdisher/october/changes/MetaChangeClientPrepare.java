package com.jeffdisher.october.changes;

import java.util.function.BiConsumer;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over locally-originated changes used in the SpeculativeProjection so that requests for delayed phase2
 * changes can be scooped out.
 */
public class MetaChangeClientPrepare implements IEntityChange
{
	private final IEntityChange _inner;
	public IEntityChange phase2;
	public long phase2DelayMillis;

	public MetaChangeClientPrepare(IEntityChange inner)
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
		LocalSink twoPhaseChangeSink = new LocalSink();
		TickProcessingContext wrapper = new TickProcessingContext(context.currentTick
				, context.previousBlockLookUp
				, context.newMutationSink
				, context.newChangeSink
				, twoPhaseChangeSink
		);
		return _inner.applyChange(wrapper, newEntity);
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}


	private class LocalSink implements BiConsumer<IEntityChange, Long>
	{
		@Override
		public void accept(IEntityChange change, Long delayMillis)
		{
			Assert.assertTrue(null == MetaChangeClientPrepare.this.phase2);
			MetaChangeClientPrepare.this.phase2 = change;
			MetaChangeClientPrepare.this.phase2DelayMillis = delayMillis;
		}
	}
}