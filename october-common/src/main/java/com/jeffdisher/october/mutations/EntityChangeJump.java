package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is how the user jumps - if they are standing on the ground, this gives them an upward movement vector.
 * Note that this doesn't move them or take time, just changes the vector.
 */
public class EntityChangeJump implements IMutationEntity
{
	/**
	 * We will make the jump force 0.6x the force of gravity (this was experimentally shown to jump just over 1 block (1.47)).
	 */
	public static final float JUMP_FORCE = -0.6f * EntityChangeMove.GRAVITY_CHANGE_PER_SECOND;

	@Override
	public long getTimeCostMillis()
	{
		// Just changes force, so takes no time.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		
		// If the entity is standing on the ground with no z-vector, we will make them jump.
		EntityLocation location = newEntity.newLocation;
		boolean isOnGround = SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, location, newEntity.original.volume());
		boolean isStatic = (0.0f == newEntity.newZVelocityPerSecond);
		if (isOnGround && isStatic)
		{
			newEntity.newZVelocityPerSecond = JUMP_FORCE;
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		// TODO:  Implement.
		throw new AssertionError("Unimplemented - stop-gap");
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		// TODO:  Implement.
		throw new AssertionError("Unimplemented - stop-gap");
	}
}
