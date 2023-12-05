package com.jeffdisher.october.changes;

import java.util.function.BiConsumer;

import com.jeffdisher.october.logic.TwoPhaseActivityManager;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over the phase 1 part of a 2-phase change.
 * This is used in the cases where the underlying change is known to be the first part of a 2-phase change, so that we
 * can intercept the request to schedule the second phase and also begin the new activity in the activity manager.
 */
public class MetaChangePhase1 implements IEntityChange
{
	private final TwoPhaseActivityManager _manager;
	private final IEntityChange _inner;
	private final long _activityToAssign;

	public MetaChangePhase1(TwoPhaseActivityManager manager, IEntityChange inner, long activityToAssign)
	{
		_manager = manager;
		_inner = inner;
		_activityToAssign = activityToAssign;
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
		boolean result = _inner.applyChange(wrapper, newEntity);
		// We expect that this scheduled something.
		Assert.assertTrue(null != twoPhaseChangeSink.change);
		_manager.scheduleNewActivity(_inner.getTargetId(), _activityToAssign, twoPhaseChangeSink.change, twoPhaseChangeSink.delayMillis);
		return result;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}


	private static class LocalSink implements BiConsumer<IEntityChange, Long>
	{
		public IEntityChange change;
		public long delayMillis;
		
		@Override
		public void accept(IEntityChange change, Long delayMillis)
		{
			Assert.assertTrue(null == this.change);
			this.change = change;
			this.delayMillis = delayMillis;
		}
	}
}
