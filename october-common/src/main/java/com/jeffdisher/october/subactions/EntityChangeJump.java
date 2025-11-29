package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This is how the user jumps - if they are standing on the ground, this gives them an upward movement vector.
 * Note that this doesn't move them or take time, just changes the vector.
 */
public class EntityChangeJump<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	/**
	 * We will make the jump force 0.6x the force of gravity (this was experimentally shown to jump just over 1 block
	 * and has a relatively "quick" feel in play testing).
	 * NOTE:  This was changed from 0.5x when the EntityActionSimpleMove was introduced to apply gravity at the start of
	 * the tick, not the end.
	 */
	public static final float JUMP_FORCE = -0.6f * EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND;
	public static final EntitySubActionType TYPE = EntitySubActionType.JUMP;

	public static boolean canJump(Function<AbsoluteLocation, BlockProxy> previousBlockLookUp
			, EntityLocation location
			, EntityVolume volume
			, EntityLocation vector
	)
	{
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), previousBlockLookUp);
		return _canJump(reader
				, location
				, volume
				, vector
		);
	}

	public static boolean canJumpWithReader(ViscosityReader reader
			, EntityLocation location
			, EntityVolume volume
			, EntityLocation vector
	)
	{
		return _canJump(reader
				, location
				, volume
				, vector
		);
	}

	public static <T extends IMutableMinimalEntity> EntityChangeJump<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		return new EntityChangeJump<>();
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		// If the entity is standing on the ground with no z-vector, we will make them jump.
		EntityLocation location = newEntity.getLocation();
		EntityLocation vector = newEntity.getVelocityVector();
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		if (_canJump(reader
				, location
				, newEntity.getType().volume()
				, vector
		))
		{
			newEntity.setVelocityVector(new EntityLocation(vector.x(), vector.y(), JUMP_FORCE));
			didApply = true;
			
			// Do other state reset.
			newEntity.resetLongRunningOperations();
			
			// Jumping expends energy.
			newEntity.applyEnergyCost(EntityActionPeriodic.ENERGY_COST_PER_JUMP);
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
		return "Jump";
	}


	private static boolean _canJump(ViscosityReader reader
			, EntityLocation location
			, EntityVolume volume
			, EntityLocation vector
	)
	{
		boolean isOnGround = SpatialHelpers.isStandingOnGround(reader, location, volume);
		// We will consider it valid to jump so long as you aren't already rising (since you can jump before the fall damage has been calculated).
		boolean isStatic = (vector.z() <= 0.0f);
		return isOnGround && isStatic;
	}
}
