package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
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

	public static <T extends IMutableMinimalEntity> EntityChangeTopLevelMovement<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation newLocation = CodecHelpers.readEntityLocation(buffer);
		EntityLocation newVelocity = CodecHelpers.readEntityLocation(buffer);
		Intensity intensity = Intensity.read(buffer);
		byte yaw = buffer.get();
		byte pitch = buffer.get();
		IMutationEntity<T> subAction = CodecHelpers.readNullableNestedChange(buffer);
		long millis = buffer.getLong();
		return new EntityChangeTopLevelMovement<>(newLocation, newVelocity, intensity, yaw, pitch, subAction, millis);
	}


	private final EntityLocation _newLocation;
	private final EntityLocation _newVelocity;
	private final Intensity _intensity;
	private final byte _yaw;
	private final byte _pitch;
	private final IMutationEntity<T> _subAction;
	private final long _millis;

	public EntityChangeTopLevelMovement(EntityLocation newLocation
		, EntityLocation newVelocity
		, Intensity intensity
		, byte yaw
		, byte pitch
		, IMutationEntity<T> subAction
		, long millis
	)
	{
		Assert.assertTrue(millis > 0L);
		
		_newLocation = newLocation;
		_newVelocity = newVelocity;
		_intensity = intensity;
		_yaw = yaw;
		_pitch = pitch;
		_subAction = subAction;
		_millis = millis;
	}

	@Override
	public long getTimeCostMillis()
	{
		// TODO:  Eventually this will be removed as the changes become full-tick.
		return _millis;
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
		
		// If there is a sub-action, run it, ignoring the result (its failure may not doom us).
		boolean subActionSuccess = false;
		if (null != _subAction)
		{
			subActionSuccess = _subAction.applyChange(context, newEntity);
		}
		else
		{
			// If there is no sub-action, clear whatever partial action we may have been performing.
			newEntity.resetLongRunningOperations();
		}
		EntityLocation startLocation = newEntity.getLocation();
		EntityLocation startVelocity = newEntity.getVelocityVector();
		EntityVolume volume = newEntity.getType().volume();
		float seconds = ((float)_millis / 1000.0f);
		float startViscosity = EntityMovementHelpers.maxViscosityInEntityBlocks(startLocation, volume, context.previousBlockLookUp);
		float startInverseViscosity = 1.0f - startViscosity;
		
		// Check the change is z-velocity from start-end.
		float newZVelocity = _newVelocity.z();
		float zVDelta = newZVelocity - startVelocity.z();
		if (0.0f == zVDelta)
		{
			// We are either at terminal velocity or standing on solid ground.
			boolean isTerminal = (newZVelocity == MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND);
			// Flat ground - make sure that they are on solid ground.
			boolean isOnGround = SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, startLocation, volume);
			if (!isTerminal && !isOnGround)
			{
				// Note that it is still possible that this is valid when running in the client where it may slice as narrowly as 1 ms.
				float startEffectiveGravity = startInverseViscosity * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
				// We want to round the expected delta.
				float expectedZDelta = EntityLocation.roundToHundredths(seconds * startEffectiveGravity);
				if (0.0f != expectedZDelta)
				{
					forceFailure = true;
				}
			}
		}
		else
		{
			float startEffectiveGravity = startInverseViscosity * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
			// We want to round the expected delta.
			float expectedZDelta = EntityLocation.roundToHundredths(seconds * startEffectiveGravity);
			boolean isValidFall = (zVDelta < (Z_VECTOR_ACCURACY_THRESHOLD * expectedZDelta));
			if (!isValidFall)
			{
				// This was not a normal fall but it might still be valid if we hit the ground or passed into a different viscosity.
				boolean didHitGround = (0.0f == _newVelocity.z()) && SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, _newLocation, volume);
				if (!didHitGround)
				{
					// This is the more expensive check so see if their new velocity is between the extremes of 2 different viscosities.
					float endViscosity = EntityMovementHelpers.maxViscosityInEntityBlocks(_newLocation, volume, context.previousBlockLookUp);
					float startTerminal = (1.0f - startViscosity) * MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
					float endTerminal = (1.0f - endViscosity) * MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
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
		}
		
		// Check that their movement was possible within their velocity (any of start/end/intensity).
		float intensityVelocityPerSecond = (Intensity.WALKING == _intensity)
			? newEntity.getType().blocksPerSecond()
			: 0.0f
		;
		float maxXVelocity = Math.max(intensityVelocityPerSecond, Math.max(_newVelocity.x(), startVelocity.x()));
		float minXVelocity = Math.min(-intensityVelocityPerSecond, Math.min(_newVelocity.x(), startVelocity.x()));
		float maxYVelocity = Math.max(intensityVelocityPerSecond, Math.max(_newVelocity.y(), startVelocity.y()));
		float minYVelocity = Math.min(-intensityVelocityPerSecond, Math.min(_newVelocity.y(), startVelocity.y()));
		float deltaX = EntityLocation.roundToHundredths(_newLocation.x() - startLocation.x());
		float deltaY = EntityLocation.roundToHundredths(_newLocation.y() - startLocation.y());
		boolean isValidDistance = ((deltaX <= EntityLocation.roundToHundredths(seconds * maxXVelocity + HORIZONTAL_SINGLE_FUDGE)) && (deltaX >= EntityLocation.roundToHundredths(seconds * minXVelocity - HORIZONTAL_SINGLE_FUDGE)))
				&& ((deltaY <= EntityLocation.roundToHundredths(seconds * maxYVelocity + HORIZONTAL_SINGLE_FUDGE)) && (deltaY >= EntityLocation.roundToHundredths(seconds * minYVelocity - HORIZONTAL_SINGLE_FUDGE)))
		;
		if (!isValidDistance)
		{
			forceFailure = true;
		}
		
		// Check that the final location is not in solid blocks (unless we aren't moving - they are allowed to stand in a solid block).
		boolean isMoving = (0.0f != deltaX) || (0.0f != deltaY);
		if (isMoving && !SpatialHelpers.canExistInLocation(context.previousBlockLookUp, _newLocation, volume))
		{
			forceFailure = true;
		}
		
		// Check that their horizontal velocity change is acceptable within their intensity.
		float xVDelta = Math.abs(_newVelocity.x() - startVelocity.x());
		float yVDelta = Math.abs(_newVelocity.y() - startVelocity.y());
		boolean isValidAcceleration = (xVDelta <= intensityVelocityPerSecond)
			&& (yVDelta <= intensityVelocityPerSecond)
		;
		boolean isNaturalDeceleration = (xVDelta == (startInverseViscosity * startVelocity.x()))
				&& (yVDelta == (startInverseViscosity * startVelocity.y()));
		if (!isValidAcceleration && !isNaturalDeceleration)
		{
			// We want to see if any of the cases related to immediate deceleration apply:  Touching the ground or hitting a wall.
			EntityLocation ray = new EntityLocation(_newLocation.x() - startLocation.x()
				, _newLocation.y() - startLocation.y()
				, _newLocation.z() - startLocation.z()
			);
			boolean[] stopX = {false};
			boolean[] stopY = {false};
			Environment env = Environment.getShared();
			EntityMovementHelpers.interactiveEntityMove(_newLocation, volume, ray, new EntityMovementHelpers.InteractiveHelper() {
				@Override
				public void setLocationAndViscosity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
				{
					if (cancelX)
					{
						stopX[0] = true;
					}
					if (cancelY)
					{
						stopY[0] = true;
					}
					if (cancelZ && SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, _newLocation, volume))
					{
						stopX[0] = true;
						stopY[0] = true;
					}
				}
				@Override
				public float getViscosityForBlockAtLocation(AbsoluteLocation location)
				{
					BlockProxy proxy = context.previousBlockLookUp.apply(location);
					float viscosity;
					if (null != proxy)
					{
						// Find the viscosity based on block type.
						viscosity = env.blocks.getViscosityFraction(proxy.getBlock(), FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE));
					}
					else
					{
						// This is missing so we will just treat it as a solid block.
						viscosity = 1.0f;
					}
					return viscosity;
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
		
		// If all checks pass, apply changes and energy cost.
		if (!forceFailure)
		{
			newEntity.setLocation(_newLocation);
			newEntity.setVelocityVector(_newVelocity);
			newEntity.setOrientation(_yaw, _pitch);
			
			// TODO:  Fix this energy attribution cost.
			int energy;
			switch (_intensity)
			{
			case STANDING:
				energy = EntityChangePeriodic.ENERGY_COST_IDLE;
				break;
			case WALKING:
				energy = EntityChangePeriodic.ENERGY_COST_MOVE_PER_BLOCK;
				break;
			default:
				throw Assert.unreachable();
			}
			newEntity.applyEnergyCost(energy);
		}
		else
		{
			System.out.println("FAIL");
		}
		
		// We will say that this succeeded if either the sub-action or the top-level movement was a success.
		return !forceFailure || subActionSuccess;
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
		buffer.putLong(_millis);
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
		return "Top-level";
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
