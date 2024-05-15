package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is how the user jumps - if they are standing on the ground, this gives them an upward movement vector.
 * Note that this doesn't move them or take time, just changes the vector.
 */
public class EntityChangeJump<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	/**
	 * We will make the jump force 0.5x the force of gravity (this was experimentally shown to jump just over 1 block
	 * and has a relatively "quick" feel in play testing).
	 */
	public static final float JUMP_FORCE = -0.5f * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
	public static final MutationEntityType TYPE = MutationEntityType.JUMP;

	public static <T extends IMutableMinimalEntity> EntityChangeJump<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeJump<>();
	}


	@Override
	public long getTimeCostMillis()
	{
		// Just changes force, so takes no time.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		// If the entity is standing on the ground with no z-vector, we will make them jump.
		EntityLocation location = newEntity.getLocation();
		boolean isOnGround = SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, location, newEntity.getVolume());
		boolean isStatic = (0.0f == newEntity.getZVelocityPerSecond());
		if (isOnGround && isStatic)
		{
			newEntity.setLocationAndVelocity(location, JUMP_FORCE);
			didApply = true;
			
			// Do other state reset.
			newEntity.resetLongRunningOperations();
			
			// Jumping expends energy.
			newEntity.applyEnergyCost(context, EntityChangePeriodic.ENERGY_COST_JUMP);
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
