package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change allows a user to swim "up" (since we use a "bobbing motion" approach to swimming).
 * It is very similar to EntityChangeJump but has different conditions and potentially different force magnitudes.
 * Note that this doesn't move them or take time, just changes the vector.
 */
public class EntityChangeSwim<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	/**
	 * We will make the swim force 0.5x the force of gravity since that is the current jump force but this will change
	 * with experimentation and "play feel" later on.
	 */
	public static final float SWIM_FORCE = -0.5f * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
	public static final float MINIMUM_UP_VELOCITY = 0.0f;
	public static final MutationEntityType TYPE = MutationEntityType.SWIM;

	public static <T extends IMutableMinimalEntity> EntityChangeSwim<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeSwim<>();
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
		Environment env = Environment.getShared();
		Block sourceBlock = env.blocks.getAsPlaceableBlock(env.items.getItemById("op.water_source"));
		
		// We will assume that we can swim if they have a low upward vector and their foot is in a water source (this logic will be made more complex, later).
		EntityLocation vector = newEntity.getVelocityVector();
		EntityLocation location = newEntity.getLocation();
		BlockProxy footBlock = context.previousBlockLookUp.apply(location.getBlockLocation());
		boolean isInWater = (sourceBlock == footBlock.getBlock());
		if (isInWater && (vector.z() <= MINIMUM_UP_VELOCITY))
		{
			newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), SWIM_FORCE));
			didApply = true;
			
			// Do other state reset.
			newEntity.resetLongRunningOperations();
			
			// Swimming expends energy.
			newEntity.applyEnergyCost(context, EntityChangePeriodic.ENERGY_COST_SWIM);
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
