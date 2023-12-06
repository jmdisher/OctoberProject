package com.jeffdisher.october.changes;

import com.jeffdisher.october.logic.TwoPhaseActivityManager;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over a standard change.  This only invalidates any existing activities.
 */
public class MetaChangeStandard implements IEntityChange
{
	private final TwoPhaseActivityManager _manager;
	private final IEntityChange _inner;

	public MetaChangeStandard(TwoPhaseActivityManager manager, IEntityChange inner)
	{
		_manager = manager;
		_inner = inner;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean result = _inner.applyChange(context, newEntity);
		_manager.nonActivityCompleted(newEntity.original.id());
		return result;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}
}
