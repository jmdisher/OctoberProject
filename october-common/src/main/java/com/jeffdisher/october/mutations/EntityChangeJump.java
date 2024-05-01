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
	 * We will make the jump force 0.5x the force of gravity (this was experimentally shown to jump just over 1 block
	 * and has a relatively "quick" feel in play testing).
	 */
	public static final float JUMP_FORCE = -0.5f * EntityChangeMove.GRAVITY_CHANGE_PER_SECOND;
	public static final MutationEntityType TYPE = MutationEntityType.JUMP;

	public static EntityChangeJump deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeJump();
	}


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
			
			// Do other state reset.
			newEntity.newLocalCraftOperation = null;
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
		// There is nothing in this type.
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
