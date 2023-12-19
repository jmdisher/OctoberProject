package com.jeffdisher.october.changes;

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
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
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


	private class LocalSink implements TickProcessingContext.ITwoPhaseChangeSink
	{
		@Override
		public void accept(int targetEntityId, IEntityChange change, long delayMillis)
		{
			Assert.assertTrue(null == MetaChangeClientPrepare.this.phase2);
			MetaChangeClientPrepare.this.phase2 = change;
			MetaChangeClientPrepare.this.phase2DelayMillis = delayMillis;
		}
	}
}
