package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionPeriodic;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This sub-action is how the user "steps up" to walk up stairs/slabs without jumping.  It can only be applied when
 * standing on the ground, with clearance above, and moving toward a sub-block which would allow this kind of climbing.
 * Mechanically, it works by increasing Z-velocity to what will decay in a single tick (so it can effectively "coast
 * forward in the air") and increasing the Z-location by 0.5 (step height).  The surrounding movement action will then
 * move it into the sub-block or it will fall back down to where it started.
 */
public class EntitySubActionStepUp<T extends IMutableMinimalEntity> implements IEntitySubAction<T>
{
	/**
	 * The step height that we use in one tick.
	 */
	public static final float MAX_STEP_HEIGHT = 0.5f;
	public static final EntitySubActionType TYPE = EntitySubActionType.STEP_UP;

	/**
	 * Creates a step-up mutation to handle this situation or null if one can't/shouldn't be produced at this time.
	 * 
	 * @param <T> The entity type.
	 * @param reader The block viscosity reader.
	 * @param location The current base location of the entity.
	 * @param volume The total volume of this entity.
	 * @param activeX The active movement being attempted in the x direction.
	 * @param activeY The active movement being attempted in the y direction.
	 * @return The appropriate step-up instance or null if one shouldn't be applied.
	 */
	public static <T extends IMutableMinimalEntity> EntitySubActionStepUp<T> buildStepUpWithReader(ViscosityReader reader
		, EntityLocation location
		, EntityVolume volume
		, float activeX
		, float activeY
	)
	{
		boolean shouldStep = false;
		
		// We can only step-up if:
		// 1)  We are currently standing on the ground.
		// 2)  We can exist in the location a step up from where we are (we won't hit the ceiling).
		// 3)  We are currently standing in only air blocks.
		EntityLocation stepStartLocation = location.getRelative(0.0f, 0.0f, MAX_STEP_HEIGHT);
		if (SpatialHelpers.isStandingOnGround(reader, location, volume)
			&& SpatialHelpers.canExistInLocation(reader, stepStartLocation, volume)
			&& (0.0f == reader.getMaxStillViscosityInVolume(stepStartLocation, volume))
		)
		{
			// We want to compare how far we would move where we are versus if we were stepped up.
			EntityLocation velocity = new EntityLocation(activeX, activeY, 0.0f);
			_CollisionHelper helper = new _CollisionHelper(reader);
			EntityMovementHelpers.interactiveEntityMove(location, volume, velocity, helper);
			EntityLocation groundLocation = helper.finalLocation;
			EntityMovementHelpers.interactiveEntityMove(stepStartLocation, volume, velocity, helper);
			EntityLocation stepLocation = helper.finalLocation;
			
			float groundDeltaX = Math.abs(groundLocation.x() - location.x());
			float groundDeltaY = Math.abs(groundLocation.y() - location.y());
			float stepDeltaX = Math.abs(stepLocation.x() - location.x());
			float stepDeltaY = Math.abs(stepLocation.y() - location.y());
			
			boolean xBetter = stepDeltaX > groundDeltaX;
			boolean yBetter = stepDeltaY > groundDeltaY;
			boolean xOk = stepDeltaX >= groundDeltaX;
			boolean yOk = stepDeltaY >= groundDeltaY;
			
			shouldStep = (xBetter && yOk) || (yBetter && xOk);
		}
		return shouldStep
			? new EntitySubActionStepUp<>(MAX_STEP_HEIGHT)
			: null
		;
	}

	public static <T extends IMutableMinimalEntity> EntitySubActionStepUp<T> deserializeFromContext(DeserializationContext context)
	{
		float stepHeight = context.buffer().getFloat();
		return new EntitySubActionStepUp<>(stepHeight);
	}


	private final float _stepHeight;

	public EntitySubActionStepUp(float stepHeight)
	{
		_stepHeight = stepHeight;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		
		// If the entity is standing on the ground with no z-vector, we will step up (we don't check that they will actually succeed to step).
		EntityLocation location = newEntity.getLocation();
		EntityLocation vector = newEntity.getVelocityVector();
		EntityVolume volume = newEntity.getType().volume();
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), context.previousBlockLookUp);
		EntityLocation stepStart = location.getRelative(0.0f, 0.0f, _stepHeight);
		if ((_stepHeight <= MAX_STEP_HEIGHT)
			&& (0.0f == vector.z())
			&& SpatialHelpers.isStandingOnGround(reader, location, volume)
			&& SpatialHelpers.canExistInLocation(reader, stepStart, volume)
		)
		{
			// We can do this so apply the change to velocity and location.
			// Note that this velocity change is intended to counter-act the natural "fall" due to gravity in this tick.
			float seconds = (float)context.millisPerTick / 1000.0f;
			float counterFall = seconds * -EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND;
			EntityLocation stepVelocity = vector.getRelative(0.0f, 0.0f, counterFall);
			
			newEntity.setLocation(stepStart);
			newEntity.setVelocityVector(stepVelocity);
			didApply = true;
			
			// Do other state reset.
			newEntity.resetLongRunningOperations();
			
			// Jumping expends energy.
			newEntity.applyEnergyCost(EntityActionPeriodic.ENERGY_COST_PER_STEP_UP);
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
		// The float of step height is stored, even though this is currently always a constant, since it is small and will likely be made variable in the future.
		buffer.putFloat(_stepHeight);
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
		return "Step-up";
	}


	private static class _CollisionHelper implements EntityMovementHelpers.IInteractiveHelper
	{
		private final ViscosityReader _reader;
		public EntityLocation finalLocation;
		public _CollisionHelper(ViscosityReader reader)
		{
			_reader = reader;
		}
		@Override
		public boolean isSolid(EntityLocation base, EntityVolume volume, boolean fromAbove)
		{
			return _reader.isSolidBlockInVolume(base, volume, fromAbove);
		}
		@Override
		public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
		{
			this.finalLocation = finalLocation;
		}
	}
}
