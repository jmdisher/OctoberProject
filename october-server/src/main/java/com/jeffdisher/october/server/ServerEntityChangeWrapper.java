package com.jeffdisher.october.server;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityType;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is a synthetic change which is used by the server to associate other tracking meta-data with a change.
 */
public class ServerEntityChangeWrapper implements IMutationEntity
{
	public final IMutationEntity realChange;
	public final int clientId;
	public final long commitLevel;

	public ServerEntityChangeWrapper(IMutationEntity realChange
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

	@Override
	public MutationEntityType getType()
	{
		// Only used in tests.
		throw Assert.unreachable();
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// Only used in tests.
		throw Assert.unreachable();
	}
}
