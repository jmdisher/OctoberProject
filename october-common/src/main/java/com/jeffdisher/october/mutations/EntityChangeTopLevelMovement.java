package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * The basis of the "new" movement design in version 1.7 where changes are now fixed to the tick period and can contain
 * a single nested operation to do more than just movement (jump/craft/place/break/etc).
 * NOTE:  As this class is how the server applies movement, this is the most likely attack vector for most forms of
 * "cheating" in the game.
 */
public class EntityChangeTopLevelMovement<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.TOP_LEVEL_MOVEMENT;
	/**
	 * We will require that the change in z-vector is within 90% of what gravity dictates (rounding errors, etc).
	 */
	public static final float Z_VECTOR_ACCURACY_THRESHOLD = 0.9f;
	/**
	 * Due to rounding errors, a single horizontal movement may need a nudge to pass checks.
	 */
	public static final float HORIZONTAL_SINGLE_FUDGE = 0.01f;
	/**
	 * When applying the "coasting" deceleration at the beginning of a tick, we assume that any speed below this is "0".
	 */
	public static final float MIN_COASTING_SPEED = 0.5f;
	/**
	 * The white-list of sub-actions which can be sent by a client.
	 */
	public static final Set<MutationEntityType> ALLOWED_TYPES = Arrays.stream(new MutationEntityType[] {
		MutationEntityType.JUMP,
		MutationEntityType.SWIM,
		MutationEntityType.BLOCK_PLACE,
		MutationEntityType.CRAFT,
		MutationEntityType.SELECT_ITEM,
		MutationEntityType.ITEMS_REQUEST_PUSH,
		MutationEntityType.ITEMS_REQUEST_PULL,
		MutationEntityType.INCREMENTAL_BREAK_BLOCK,
		MutationEntityType.CRAFT_IN_BLOCK,
		MutationEntityType.ATTACK_ENTITY,
		MutationEntityType.USE_SELECTED_ITEM_ON_SELF,
		MutationEntityType.USE_SELECTED_ITEM_ON_BLOCK,
		MutationEntityType.USE_SELECTED_ITEM_ON_ENTITY,
		MutationEntityType.CHANGE_HOTBAR_SLOT,
		MutationEntityType.SWAP_ARMOUR,
		MutationEntityType.SET_BLOCK_LOGIC_STATE,
		MutationEntityType.SET_DAY_AND_SPAWN,
		MutationEntityType.INCREMENTAL_REPAIR_BLOCK,
		MutationEntityType.MULTI_BLOCK_PLACE,
		MutationEntityType.TESTING_ONLY,
	}).collect(Collectors.toSet());

	public static <T extends IMutableMinimalEntity> EntityChangeTopLevelMovement<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		EntityLocation newVelocity = CodecHelpers.readEntityLocation(buffer);
		Intensity intensity = Intensity.read(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IMutationEntity<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		return new EntityChangeTopLevelMovement<>(newLocation, newVelocity, intensity, yaw, pitch, subAction);
	}

	/**
	 * Computes the velocity in a given axis after accounting for coasting through viscosity and accounting for coasting
	 * limits.
	 * 
	 * @param viscosity The viscosity of the blocks where the entity is.
	 * @param axisVelocity The velocity, in blocks/s, in one axis from the previous tick.
	 * @return The new coasting velocity for this axis.
	 */
	public static float velocityAfterViscosityAndCoast(float viscosity, float axisVelocity)
	{
		return _velocityAfterViscosityAndCoast(viscosity, axisVelocity);
	}


	private final EntityLocation _newLocation;
	private final EntityLocation _newVelocity;
	private final Intensity _intensity;
	private final byte _yaw;
	private final byte _pitch;
	private final IMutationEntity<T> _subAction;

	public EntityChangeTopLevelMovement(EntityLocation newLocation
		, EntityLocation newVelocity
		, Intensity intensity
		, byte yaw
		, byte pitch
		, IMutationEntity<T> subAction
	)
	{
		Assert.assertTrue((null == subAction) || ALLOWED_TYPES.contains(subAction.getType()));
		
		_newLocation = newLocation;
		_newVelocity = newVelocity;
		_intensity = intensity;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, T newEntity)
	{
		// For now, we will just verify the inputs relatively simply:
		// -run the sub-action first (since it may impact velocity)
		// -verify that the change in z-velocity from post sub-action to the final result is within gravity acceleration
		//  expectations, given some error margin
		// -if they remain on the same z-level, make sure that they are on solid blocks where they started
		// -make sure that their final location is one where they can physically stand
		// -make sure that their final X/Y offsets are possible within their max(v_start, v_end, v_intensity)
		// -make sure that their horizontal change in velocity is permitted by the intensity
		boolean forceFailure = false;
		
		// TODO:  In the future, add more checks, as needed.  Some possible examples:
		// -make sure that there is a valid path from start to end (normally, this is less than a block but may matter
		//  for high speeds, like falling at terminal velocity)
		// -tighten checks on the change in z-vector to make sure that they are falling at a reasonable rate
		// -verify collisions with walls/ceilings/floors when velocity drops very quickly
		
		// If there is a sub-action, run it (we require that any sub-action is also a success).
		if (null != _subAction)
		{
			boolean subActionSuccess = _subAction.applyChange(context, newEntity);
			if (!subActionSuccess)
			{
				_log("Fail0", newEntity);
				forceFailure = true;
			}
		}
		else
		{
			// If there is no sub-action, clear whatever partial action we may have been performing.
			newEntity.resetLongRunningOperations();
		}
		EntityLocation startLocation = newEntity.getLocation();
		EntityLocation startVelocity = newEntity.getVelocityVector();
		EntityVolume volume = newEntity.getType().volume();
		float seconds = ((float)context.millisPerTick / 1000.0f);
		float startViscosity = EntityMovementHelpers.maxViscosityInEntityBlocks(startLocation, volume, context.previousBlockLookUp);
		float startInverseViscosity = 1.0f - startViscosity;
		
		Environment env = Environment.getShared();
		ViscosityReader reader = new ViscosityReader(env, context.previousBlockLookUp);
		
		// Check the change is z-velocity from start-end.
		if (!forceFailure)
		{
			float newZVelocity = _newVelocity.z();
			float zVDelta = newZVelocity - startVelocity.z();
			if (0.0f == zVDelta)
			{
				forceFailure = _verifyZVelocityUnchanged(reader
					, startLocation
					, volume
					, seconds
					, startViscosity
					, startInverseViscosity
					, newZVelocity
				);
				if (forceFailure)
				{
					_log("Fail1", newEntity);
				}
			}
			else
			{
				forceFailure = _verifyZVelocityChange(context
					, reader
					, startLocation
					, volume
					, seconds
					, startViscosity
					, startInverseViscosity
					, newZVelocity
					, zVDelta
				);
				if (forceFailure)
				{
					_log("Fail2", newEntity);
				}
			}
		}
		
		// Check that their movement was possible within their velocity (any of start/end/intensity).
		float intensityVelocityPerSecond = (Intensity.WALKING == _intensity)
			? newEntity.getType().blocksPerSecond()
			: 0.0f
		;
		float deltaX = EntityLocation.roundToHundredths(_newLocation.x() - startLocation.x());
		float deltaY = EntityLocation.roundToHundredths(_newLocation.y() - startLocation.y());
		if (!forceFailure)
		{
			forceFailure = _verifyHorizontalMovement(startVelocity
				, seconds
				, intensityVelocityPerSecond
				, deltaX
				, deltaY
			);
			if (forceFailure)
			{
				_log("Fail3", newEntity);
			}
		}
		
		// Check that the final location is not in solid blocks (unless we aren't moving - they are allowed to stand in a solid block).
		if (!forceFailure)
		{
			forceFailure = _verifyFinalLocationValid(reader
				, volume
				, deltaX
				, deltaY
			);
			if (forceFailure)
			{
				_log("Fail4", newEntity);
			}
		}
		
		// Check that their horizontal velocity change is acceptable within their intensity.
		if (!forceFailure)
		{
			forceFailure = _verifyHorizontalVelocity(reader
				, startLocation
				, startVelocity
				, volume
				, startInverseViscosity
				, intensityVelocityPerSecond
			);
			if (forceFailure)
			{
				_log("Fail5", newEntity);
			}
		}
		
		// If all checks pass, apply changes and energy cost.
		if (!forceFailure)
		{
			newEntity.setLocation(_newLocation);
			newEntity.setVelocityVector(_newVelocity);
			newEntity.setOrientation(_yaw, _pitch);
			
			// We only use energy when walking (standing changes are often just dropped so they shouldn't cost anything - idle cost is already added in periodic).
			if (Intensity.WALKING == _intensity)
			{
				newEntity.applyEnergyCost(EntityChangePeriodic.ENERGY_COST_PER_TICK_WALKING);
			}
		}
		return !forceFailure;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _newLocation);
		CodecHelpers.writeEntityLocation(buffer, _newVelocity);
		Intensity.write(buffer, _intensity);
		buffer.put(_yaw);
		buffer.put(_pitch);
		CodecHelpers.writeNullableNestedChange(buffer, _subAction);
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
		return String.format("Top-Level(%s), L(%s), V(%s), Sub: %s", _intensity, _newLocation, _newVelocity, _subAction);
	}

	/**
	 * Provided purely so that tests can verify the internal sub-action is what they expect, since this is otherwise
	 * opaque and now contains the details of actions which were previously in smaller instances.
	 * 
	 * @return The sub-action.
	 */
	public IMutationEntity<T> test_getSubAction()
	{
		return _subAction;
	}


	private static float _velocityAfterViscosityAndCoast(float viscosity, float axisVelocity)
	{
		float newVelocity = 0.5f * viscosity * axisVelocity;
		if (Math.abs(newVelocity) < MIN_COASTING_SPEED)
		{
			newVelocity = 0.0f;
		}
		return EntityLocation.roundToHundredths(newVelocity);
	}

	private void _log(String title, T newEntity)
	{
		System.out.printf("%s - (%s) against L(%s), V(%s)\n", title, this, newEntity.getLocation(), newEntity.getVelocityVector());
	}

	private boolean _verifyZVelocityUnchanged(ViscosityReader reader
		, EntityLocation startLocation
		, EntityVolume volume
		, float seconds
		, float startViscosity
		, float startInverseViscosity
		, float newZVelocity
	)
	{
		boolean forceFailure = false;
		// We are either at terminal velocity or standing on solid ground.
		boolean isTerminal = (newZVelocity == EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND);
		// Flat ground - make sure that they are on solid ground.
		boolean isOnGround = SpatialHelpers.isStandingOnGround(reader, startLocation, volume);
		// Alternatively, they may be swimming (creatures can swim up at a sustained velocity).
		boolean isSwimmable = (int)(startViscosity * 100.0f) >= BlockAspect.SWIMMABLE_VISCOSITY;
		if (!isTerminal && !isOnGround && !isSwimmable)
		{
			// Note that it is still possible that this is valid when running in the client where it may slice as narrowly as 1 ms.
			float startEffectiveGravity = startInverseViscosity * EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND;
			// We want to round the expected delta.
			float expectedZDelta = EntityLocation.roundToHundredths(seconds * startEffectiveGravity);
			if (0.0f != expectedZDelta)
			{
				forceFailure = true;
			}
		}
		return forceFailure;
	}

	private boolean _verifyZVelocityChange(TickProcessingContext context
			, ViscosityReader reader
		, EntityLocation startLocation
		, EntityVolume volume
		, float seconds
		, float startViscosity
		, float startInverseViscosity
		, float newZVelocity
		, float zVDelta
	)
	{
		boolean forceFailure = false;
		float startEffectiveGravity = startInverseViscosity * EntityMovementHelpers.GRAVITY_CHANGE_PER_SECOND;
		// We want to round the expected delta.
		float expectedZDelta = EntityLocation.roundToHundredths(seconds * startEffectiveGravity);
		boolean isValidFall = (zVDelta < (Z_VECTOR_ACCURACY_THRESHOLD * expectedZDelta));
		if (!isValidFall)
		{
			// This was not a normal fall but it might still be valid if we hit the ground or passed into a different viscosity.
			// (note that we may have started on the ground in our previous change and that would also count).
			boolean didHitGround = (0.0f == _newVelocity.z())
				&& (SpatialHelpers.isStandingOnGround(reader, _newLocation, volume) || SpatialHelpers.isStandingOnGround(reader, startLocation, volume))
			;
			if (!didHitGround)
			{
				// This is the more expensive check so see if their new velocity is between the extremes of 2 different viscosities.
				float endViscosity = EntityMovementHelpers.maxViscosityInEntityBlocks(_newLocation, volume, context.previousBlockLookUp);
				float startTerminal = (1.0f - startViscosity) * EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
				float endTerminal = (1.0f - endViscosity) * EntityMovementHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
				boolean isTransitionVelocity = ((endTerminal <= newZVelocity) && (newZVelocity <= startTerminal))
					|| ((startTerminal <= newZVelocity) && (newZVelocity <= endTerminal))
				;
				if (!isTransitionVelocity)
				{
					// We still have no valid explanation for this.
					forceFailure = true;
				}
			}
		}
		return forceFailure;
	}

	private boolean _verifyHorizontalMovement(EntityLocation startVelocity
		, float seconds
		, float intensityVelocityPerSecond
		, float deltaX
		, float deltaY
	)
	{
		boolean forceFailure = false;
		float maxXVelocity = Math.max(intensityVelocityPerSecond, Math.max(_newVelocity.x(), startVelocity.x()));
		float minXVelocity = Math.min(-intensityVelocityPerSecond, Math.min(_newVelocity.x(), startVelocity.x()));
		float maxYVelocity = Math.max(intensityVelocityPerSecond, Math.max(_newVelocity.y(), startVelocity.y()));
		float minYVelocity = Math.min(-intensityVelocityPerSecond, Math.min(_newVelocity.y(), startVelocity.y()));
		boolean isValidDistance = ((deltaX <= EntityLocation.roundToHundredths(seconds * maxXVelocity + HORIZONTAL_SINGLE_FUDGE)) && (deltaX >= EntityLocation.roundToHundredths(seconds * minXVelocity - HORIZONTAL_SINGLE_FUDGE)))
			&& ((deltaY <= EntityLocation.roundToHundredths(seconds * maxYVelocity + HORIZONTAL_SINGLE_FUDGE)) && (deltaY >= EntityLocation.roundToHundredths(seconds * minYVelocity - HORIZONTAL_SINGLE_FUDGE)))
		;
		if (!isValidDistance)
		{
			forceFailure = true;
		}
		return forceFailure;
	}

	private boolean _verifyFinalLocationValid(ViscosityReader reader
		, EntityVolume volume
		, float deltaX
		, float deltaY
	)
	{
		boolean forceFailure = false;
		boolean isMoving = (0.0f != deltaX) || (0.0f != deltaY);
		if (isMoving && !SpatialHelpers.canExistInLocation(reader, _newLocation, volume))
		{
			forceFailure = true;
		}
		return forceFailure;
	}

	private boolean _verifyHorizontalVelocity(ViscosityReader reader
		, EntityLocation startLocation
		, EntityLocation startVelocity
		, EntityVolume volume
		, float startInverseViscosity
		, float intensityVelocityPerSecond
	)
	{
		boolean forceFailure = false;
		float xVDelta = Math.abs(_newVelocity.x() - startVelocity.x());
		float yVDelta = Math.abs(_newVelocity.y() - startVelocity.y());
		boolean isValidAcceleration = (xVDelta <= intensityVelocityPerSecond)
			&& (yVDelta <= intensityVelocityPerSecond)
		;
		float deceleratedX = _velocityAfterViscosityAndCoast(startInverseViscosity, startVelocity.x());
		float deceleratedY = _velocityAfterViscosityAndCoast(startInverseViscosity, startVelocity.y());
		boolean isNaturalDeceleration = (deceleratedX == _newVelocity.x()) && (deceleratedY == _newVelocity.y());
		if (!isValidAcceleration && !isNaturalDeceleration)
		{
			// We want to see if any of the cases related to immediate deceleration apply:  Touching the ground or hitting a wall.
			EntityLocation ray = new EntityLocation(_newLocation.x() - startLocation.x()
				, _newLocation.y() - startLocation.y()
				, _newLocation.z() - startLocation.z()
			);
			boolean[] stopX = {false};
			boolean[] stopY = {false};
			if (SpatialHelpers.isStandingOnGround(reader, startLocation, volume))
			{
				stopX[0] = true;
				stopY[0] = true;
			}
			EntityMovementHelpers.interactiveEntityMove(_newLocation, volume, ray, new EntityMovementHelpers.InteractiveHelper() {
				@Override
				public void setLocationAndCancelVelocity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
				{
					if (cancelX)
					{
						stopX[0] = true;
					}
					if (cancelY)
					{
						stopY[0] = true;
					}
					if (cancelZ && SpatialHelpers.isStandingOnGround(reader, _newLocation, volume))
					{
						stopX[0] = true;
						stopY[0] = true;
					}
				}
				@Override
				public float getViscosityForBlockAtLocation(AbsoluteLocation location)
				{
					return reader.getViscosityFraction(location);
				}
			});
			boolean touchingSurface = (((0.0f == _newVelocity.x()) == stopX[0])
					&& ((0.0f == _newVelocity.y()) == stopY[0])
			);
			if (!touchingSurface)
			{
				forceFailure = true;
			}
		}
		return forceFailure;
	}


	public static enum Intensity
	{
		STANDING,
		WALKING,
		;
		public static Intensity read(ByteBuffer buffer)
		{
			byte ordinal = buffer.get();
			return Intensity.values()[ordinal];
		}
		public static void write(ByteBuffer buffer, Intensity intensity)
		{
			byte ordinal = (byte)intensity.ordinal();
			buffer.put(ordinal);
		}
	}

	public static final float MULTIPLIER_FORWARD = 1.0f;
	public static final float MULTIPLIER_STRAFE = 0.8f;
	public static final float MULTIPLIER_BACKWARD = 0.6f;
	/**
	 * The direction of a horizontal move, relative to the orientation.
	 */
	public static enum Relative
	{
		FORWARD(MULTIPLIER_FORWARD, 0.0f),
		RIGHT(MULTIPLIER_STRAFE, (float)(3.0 / 2.0 * Math.PI)),
		LEFT(MULTIPLIER_STRAFE, (float)(1.0 / 2.0 * Math.PI)),
		BACKWARD(MULTIPLIER_BACKWARD, (float)Math.PI),
		;
		public final float speedMultiplier;
		public final float yawRadians;
		private Relative(float speedMultiplier, float yawRadians)
		{
			this.speedMultiplier = speedMultiplier;
			this.yawRadians = yawRadians;
		}
	}
}
