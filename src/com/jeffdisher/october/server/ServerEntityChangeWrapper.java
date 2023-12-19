package com.jeffdisher.october.server;

import com.jeffdisher.october.changes.IEntityChange;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is a synthetic change which is used by the server to associate other tracking meta-data with a change.
 */
public class ServerEntityChangeWrapper implements IEntityChange
{
	public final IEntityChange realChange;
	public final int clientId;
	public final long commitLevel;

	public ServerEntityChangeWrapper(IEntityChange realChange
			, int clientId
			, long commitLevel
	)
	{
		this.realChange = realChange;
		this.clientId = clientId;
		this.commitLevel = commitLevel;
	}

	@Override
	public long getTimeCostMillis()
	{
		return this.realChange.getTimeCostMillis();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		return this.realChange.applyChange(context, newEntity);
	}
}
