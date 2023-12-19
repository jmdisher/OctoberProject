package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


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
	public long getTimeCostMillis()
	{
		// Just treat this as free.
		return 0;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean result = this.inner.applyChange(context, newEntity);
		this.wasSuccess = result;
		return result;
	}
}
