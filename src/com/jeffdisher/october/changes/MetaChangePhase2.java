package com.jeffdisher.october.changes;

import com.jeffdisher.october.logic.TwoPhaseActivityManager;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over the phase 2 part of a 2-phase change.
 * This is used when the underlying change was scheduled as the second part of a 2-phase change so that we can intercept
 * the result and use it to finish the transaction in the activity manager.
 */
public class MetaChangePhase2 implements IEntityChange
{
	private final TwoPhaseActivityManager _manager;
	private final IEntityChange _inner;
	private final long _activityId;

	public MetaChangePhase2(TwoPhaseActivityManager manager, IEntityChange inner, long activityId)
	{
		_manager = manager;
		_inner = inner;
		_activityId = activityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean result = _inner.applyChange(context, newEntity);
		_manager.activityCompleted(newEntity.original.id(), _activityId, result);
		return result;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}
}
