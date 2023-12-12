package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over the phase 2 part of a 2-phase change used on the server.
 * This is used when the underlying change was scheduled as the second part of a 2-phase change so that we can intercept
 * the result and use it to finish the activity.
 */
public class MetaChangePhase2 implements IEntityChange
{
	public final IEntityChange inner;
	public final int clientId;
	public final long activityId;
	public boolean wasSuccess;

	public MetaChangePhase2(IEntityChange inner, int clientId, long activityId)
	{
		this.inner = inner;
		this.clientId = clientId;
		this.activityId = activityId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean result = this.inner.applyChange(context, newEntity);
		this.wasSuccess = result;
		return result;
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}
}
