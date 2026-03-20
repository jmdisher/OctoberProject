package com.jeffdisher.october.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.ScheduledChange;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.EntityActionCodec;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;


/**
 * This class just contains the logic to serialize and deserialize a latest-version Entity to/from a ByteBuffer.
 * Note that the version header is neither read nor written in these helpers.
 */
public class EntityCodec
{
	public static void serializeEntityWithoutVersionHeader(ByteBuffer outBuffer, SuspendedEntity suspended)
	{
		// For the most part, we just use the existing network codec and the action codec.
		Entity entity = suspended.entity();
		List<ScheduledChange> changes = suspended.changes();
		CodecHelpers.writeEntityDisk(outBuffer, entity);
		
		// We now write any suspended changes.
		for (ScheduledChange scheduled : changes)
		{
			// Check that this kind of change can be stored to disk (some have ephemeral references and should be dropped).
			if (scheduled.change().canSaveToDisk())
			{
				// Write the parts of the data.
				outBuffer.putLong(scheduled.millisUntilReady());
				EntityActionCodec.serializeToBuffer(outBuffer, scheduled.change());
			}
		}
	}

	public static SuspendedEntity deserializeEntityWithoutVersionHeader(ByteBuffer inBuffer
		, long currentGameMillis
	)
	{
		Environment env = Environment.getShared();
		boolean usePreV8NonStackableDecoding = false;
		boolean usePreV11DamageDecoding = false;
		DeserializationContext context = new DeserializationContext(env
			, inBuffer
			, currentGameMillis
			, usePreV8NonStackableDecoding
			, usePreV11DamageDecoding
		);
		
		Entity entity = CodecHelpers.readEntityDisk(context);
		
		// Now, load any suspended changes.
		List<ScheduledChange> suspended = _readSuspendedMutations(context);
		return new SuspendedEntity(entity, suspended);
	}

	public static List<ScheduledChange> readSuspendedMutations(DeserializationContext context)
	{
		return _readSuspendedMutations(context);
	}


	private static List<ScheduledChange> _readSuspendedMutations(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		List<ScheduledChange> suspended = new ArrayList<>();
		while (buffer.hasRemaining())
		{
			// Read the parts of the suspended data.
			long millisUntilReady = buffer.getLong();
			IEntityAction<IMutablePlayerEntity> change = EntityActionCodec.parseAndSeekContext(context);
			suspended.add(new ScheduledChange(change, millisUntilReady));
		}
		return suspended;
	}
}
