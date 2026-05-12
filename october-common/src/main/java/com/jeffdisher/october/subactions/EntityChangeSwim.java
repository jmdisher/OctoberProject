package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This change allows a user to swim "up" (since we use a "bobbing motion" approach to swimming).
 * It is very similar to EntityChangeJump but has different conditions and potentially different force magnitudes.
 * Note that this doesn't move them or take time, just changes the vector.
 */
public class EntityChangeSwim<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	/**
	 * We will make the swim force 0.6x the force of gravity since that is the current jump force but this will change
	 * with experimentation and "play feel" later on.
	 * NOTE:  This was changed from 0.5x when the EntityActionSimpleMove was introduced to apply gravity at the start of
	 * the tick, not the end.
	 */
	public static final float SWIM_FORCE = -0.6f * EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND;
	public static final EntitySubActionType TYPE = EntitySubActionType.SWIM;

	public static boolean canSwim(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation location
		, EntityVolume volume
		, EntityLocation vector
	)
	{
		return _canSwim(previousBlockLookUp
			, location
			, volume
			, vector
		);
	}

	public static <T extends IMutableMinimalEntity> EntityChangeSwim<T> deserializeFromContext(DeserializationContext context)
	{
		return new EntityChangeSwim<>();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		// We will assume that we can swim if they have a low upward vector and their foot is in a water source (this logic will be made more complex, later).
		EntityLocation vector = newEntity.getVelocityVector();
		EntityLocation location = newEntity.getLocation();
		if (_canSwim(context.previousBlockLookUp
			, location
			, newEntity.getType().volume()
			, vector
		))
		{
			float newZVector = vector.z() < 0.0f
				? (vector.z() + SWIM_FORCE)
				: SWIM_FORCE
			;
			newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), newZVector));
			didApply = true;
			
			// Do other state reset.
			newEntity.resetLongRunningOperations();
			
			// Swimming expends energy.
			newEntity.applyEnergyCost(EntityActionPeriodic.ENERGY_COST_PER_SWIM_UP);
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
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

	@Override
	public String toString()
	{
		return "Swim";
	}


	private static boolean _canSwim(TickProcessingContext.IBlockFetcher previousBlockLookUp
		, EntityLocation location
		, EntityVolume volume
		, EntityLocation vector
	)
	{
		boolean canSwim = false;
		// Check that we aren't already drifting up.
		if (vector.z() <= 0.0f)
		{
			// Now, make sure that the viscosity we occupy is less than solid but enough to be swimmable.
			Environment env = Environment.getShared();
			ViscosityReader reader = new ViscosityReader(env, previousBlockLookUp);
			float maxViscosity = reader.getMaxStillViscosityInVolume(location, volume);
			float swimmable = (float)BlockAspect.SWIMMABLE_VISCOSITY / 100.0f;
			canSwim = (maxViscosity < 1.0f) && (maxViscosity >= swimmable);
		}
		return canSwim;
	}
}
