package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over the phase 1 part of a 2-phase change used on the server.
 * This is used in the cases where the underlying change is known to be the first part of a 2-phase change, so that we
 * can intercept the request to schedule the second phase and also track the multi-phase activity.
 */
public class MetaChangePhase1 implements IEntityChange
{
	public final IEntityChange inner;
	public final int clientId;
	public final long commitLevel;
	public IEntityChange phase2;
	public long phase2DelayMillis;

	public MetaChangePhase1(IEntityChange inner, int clientId, long commitLevel)
	{
		this.inner = inner;
		this.clientId = clientId;
		this.commitLevel = commitLevel;
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
		boolean result = this.inner.applyChange(wrapper, newEntity);
		if (result)
		{
			// We track what was scheduled so it can be scooped out, later.
			this.phase2 = twoPhaseChangeSink.change;
			this.phase2DelayMillis = twoPhaseChangeSink.delayMillis;
		}
		else
		{
			// Otherwise, we ignore the result since we weren't going to schedule it, anyway.
		}
		return result;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}


	private static class LocalSink implements TickProcessingContext.ITwoPhaseChangeSink
	{
		public IEntityChange change;
		public long delayMillis;
		
		@Override
		public void accept(int targetEntityId, IEntityChange change, long delayMillis)
		{
			Assert.assertTrue(null == this.change);
			this.change = change;
			this.delayMillis = delayMillis;
		}
	}
}
