package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.IMutableMinimalEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 */
public class EntityChangeDoNothing<T extends IMutableMinimalEntity> implements IMutationEntity<T>
{
	public static final MutationEntityType TYPE = MutationEntityType.DO_NOTHING;

	/**
	 * We limit the time cost of a single movement to 100 ms.  This is typically the setting for a single server-side
	 * tick but will work properly if not.
	 */
	public static final long LIMIT_COST_MILLIS = 100L;

	public static <T extends IMutableMinimalEntity> EntityChangeDoNothing<T> deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation oldLocation = CodecHelpers.readEntityLocation(buffer);
		long millisPassed = buffer.getLong();
		return new EntityChangeDoNothing<>(oldLocation, millisPassed);
	}


	private final EntityLocation _oldLocation;
	private final long _millisPassed;

	public EntityChangeDoNothing(EntityLocation oldLocation, long millisPassed)
	{
		// Make sure that this is valid within our limits.
		// TODO:  Define a better failure mode when the server deserializes these from the network.
		Assert.assertTrue(millisPassed <= LIMIT_COST_MILLIS);
		
		_oldLocation = oldLocation;
		_millisPassed = millisPassed;
	}

	@Override
	public long getTimeCostMillis()
	{
		return _millisPassed;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutableMinimalEntity newEntity)
	{
		boolean didApply = false;
		boolean oldDoesMatch = _oldLocation.equals(newEntity.getLocation());
		if (oldDoesMatch)
		{
			didApply = _handleMotion(context, newEntity, _millisPassed);
			
			if (didApply)
			{
				// Do other state reset now that we are moving.
				newEntity.resetLongRunningOperations();
			}
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
		CodecHelpers.writeEntityLocation(buffer, _oldLocation);
		buffer.putLong(_millisPassed);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}


	private static boolean _handleMotion(TickProcessingContext context
			, IMutableMinimalEntity newEntity
			, long longMillisInMotion
	)
	{
		// First of all, we need to figure out if we should be changing our z-vector:
		// -cancel positive vector if we hit the ceiling
		// -cancel negative vector if we hit the ground
		// -apply gravity in any other case
		float initialZVector = newEntity.getZVelocityPerSecond();
		EntityLocation oldLocation = newEntity.getLocation();
		EntityVolume volume = newEntity.getVolume();
		float secondsInMotion = ((float)longMillisInMotion) / MotionHelpers.FLOAT_MILLIS_PER_SECOND;
		float newZVector;
		boolean shouldAllowFalling;
		if ((initialZVector > 0.0f) && SpatialHelpers.isTouchingCeiling(context.previousBlockLookUp, oldLocation, volume))
		{
			// We are up against the ceiling so cancel the velocity.
			newZVector = 0.0f;
			shouldAllowFalling = true;
		}
		else if ((initialZVector <= 0.0f) && SpatialHelpers.isStandingOnGround(context.previousBlockLookUp, oldLocation, volume))
		{
			// We are on the ground so cancel the velocity.
			newZVector = 0.0f;
			shouldAllowFalling = false;
		}
		else
		{
			newZVector = MotionHelpers.applyZAcceleration(initialZVector, secondsInMotion);
			shouldAllowFalling = true;
		}
		
		boolean didMove;
		if (shouldAllowFalling)
		{
			// Figure out where our new location is (requires calculating the z-movement in this time).
			float zDistance = shouldAllowFalling
					? MotionHelpers.applyZMovement(initialZVector, secondsInMotion)
					: 0.0f
			;
			float oldZ = oldLocation.z();
			float zLocation = oldZ + zDistance;
			EntityLocation newLocation = new EntityLocation(oldLocation.x(), oldLocation.y(), zLocation);
			
			if (SpatialHelpers.canExistInLocation(context.previousBlockLookUp, newLocation, volume))
			{
				// They can exist in the target location so update the entity.
				newEntity.setLocationAndVelocity(newLocation, newZVector);
				didMove = true;
			}
			else
			{
				// This means that we hit the ceiling, floor, or are stuck in a block.
				if (zDistance > 0.0f)
				{
					// We were jumping to see if we can clamp our location under the block.
					EntityLocation highestLocation = SpatialHelpers.locationTouchingCeiling(context.previousBlockLookUp, newLocation, volume, oldZ);
					if (null != highestLocation)
					{
						newEntity.setLocationAndVelocity(highestLocation, newZVector);
						didMove = true;
					}
					else
					{
						// We can't find a ceiling we can fit under so we are inside a block.
						didMove = false;
					}
				}
				else if (zDistance < 0.0f)
				{
					// We were falling so see if we can stop on the block(s) above where we fell.
					EntityLocation lowestLocation = SpatialHelpers.locationTouchingGround(context.previousBlockLookUp, newLocation, volume, oldZ);
					if (null != lowestLocation)
					{
						newEntity.setLocationAndVelocity(lowestLocation, newZVector);
						didMove = true;
					}
					else
					{
						// We can't find a floor we can land on so we are probably inside a block.
						didMove = false;
					}
				}
				else
				{
					// We must be inside a block.
					didMove = false;
				}
			}
		}
		else
		{
			// No z-movement.
			didMove = false;
		}
		
		// Note that waiting around doesn't expend any energy.
		
		// We consider this having applied if we moved OR we at least updated the z-vector.
		boolean didUpdateVelocity = false;
		if (!didMove && (newZVector != initialZVector))
		{
			newEntity.setLocationAndVelocity(oldLocation, newZVector);
			didUpdateVelocity = true;
		}
		return didMove || didUpdateVelocity;
	}
}
