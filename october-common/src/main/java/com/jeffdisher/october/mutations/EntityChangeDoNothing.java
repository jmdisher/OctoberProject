package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 */
public class EntityChangeDoNothing<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DO_NOTHING;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static <T extends IMutableMinimalEntity> EntityChangeDoNothing<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation oldLocation = CodecHelpers.readEntityLocation(buffer);
		long millisPassed = buffer.getLong();
		return new EntityChangeDoNothing<>(oldLocation, millisPassed);
	}


	private final EntityLocation _oldLocation;
	private final long _millisPassed;

	public EntityChangeDoNothing(EntityLocation oldLocation, long millisPassed)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(millisPassed <= LIMIT_COST_MILLIS);
		
		_oldLocation = oldLocation;
		_millisPassed = millisPassed;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisPassed;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		boolean oldDoesMatch = _oldLocation.equals(newEntity.getLocation());
		if (oldDoesMatch)
		{
			didApply = EntityMovementHelpers.allowMovement(context, newEntity, _millisPassed);
			
			if (didApply)
			{
				// Do other state reset now that we are moving.
				newEntity.resetLongRunningOperations();
			}
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _oldLocation);
		buffer.putLong(_millisPassed);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
