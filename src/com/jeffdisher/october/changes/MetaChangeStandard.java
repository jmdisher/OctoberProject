package com.jeffdisher.october.changes;

import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Wrapper over a standard change used on the server.  This is used to track information related to how to update the
 * client commit level, upon completion.
 */
public class MetaChangeStandard implements IEntityChange
{
	public final IEntityChange inner;
	public final int clientId;
	public final long commitLevel;

	public MetaChangeStandard(IEntityChange inner, int clientId, long commitLevel)
	{
		this.inner = inner;
		this.clientId = clientId;
		this.commitLevel = commitLevel;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		return this.inner.applyChange(context, newEntity);
	}

	@Override
	public boolean canReplacePrevious(IEntityChange previousChange)
	{
		// This is only an internal wrapper so this check is never called.
		throw Assert.unreachable();
	}
}
