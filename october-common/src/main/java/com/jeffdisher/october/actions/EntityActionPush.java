package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * This is similar to PassiveActionPush but for players and creatures.
 * This differs from the "NUDGE" action in that it directly shifts their location, not going via velocity.  This is
 * because the push is expected in cases where the world moves them, directly, not just influencing movement.
 * An example of the difference is hitting an entity will nudge them by velocity whereas a block mover actually pushing
 * them, directly, needs to move them over by this direct amount.
 * NOTE:  As this directly changes location, which players own in the client, this is likely to cause some conflicting
 * or otherwise glitchy movement.
 */
public class EntityActionPush<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.PUSH_ENTITY;

	public static <T extends IMutableMinimalEntity> EntityActionPush<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		EntityLocation distance = CodecHelpers.readEntityLocation(buffer);
		return new EntityActionPush<>(distance);
	}


	private final EntityLocation _pushDistance;

	public EntityActionPush(EntityLocation pushDistance)
	{
		Assert.assertTrue(null != pushDistance);
		
		_pushDistance = pushDistance;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		EntityLocation originalLocation = newEntity.getLocation();
		EntityLocation newLocation = originalLocation.getRelativeForLocation(_pushDistance);
		newEntity.setLocation(newLocation);
		return true;
	}

	@Override
	public EntityActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _pushDistance);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Push by " + _pushDistance;
	}
}
