package com.jeffdisher.october.actions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.mutations.EntityActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntityAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Pushes an entity (the entity running the receiver) with a specific directional force.
 * This is done by adding the force to their velocity.  The next time EntityActionSimpleMove is run against the entity,
 * this velocity will be used to push them.
 */
public class EntityActionNudge<T extends IMutableMinimalEntity> implements IEntityAction<T>
{
	public static final EntityActionType TYPE = EntityActionType.NUDGE_ENTITY;

	public static <T extends IMutableMinimalEntity> EntityActionNudge<T> deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		EntityLocation force = CodecHelpers.readEntityLocation(buffer);
		return new EntityActionNudge<>(force);
	}


	private final EntityLocation _force;

	public EntityActionNudge(EntityLocation force)
	{
		Assert.assertTrue(null != force);
		
		_force = force;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		EntityLocation originalVelocity = newEntity.getVelocityVector();
		EntityLocation combinedVelocity = new EntityLocation(originalVelocity.x() + _force.x()
			, originalVelocity.y() + _force.y()
			, originalVelocity.z() + _force.z()
		);
		newEntity.setVelocityVector(combinedVelocity);
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
		CodecHelpers.writeEntityLocation(buffer, _force);
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
		return "Nudge by " + _force;
	}
}
