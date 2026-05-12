package com.jeffdisher.october.client;

import com.jeffdisher.october.actions.EntityActionCreativeFlight;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This is similar to MovementAccumulator but for the cases where creative flight is active.  The internal accumulation
 * is much simpler as there is no consideration for viscosity or collision with EntityActionCreativeFlight so all that
 * is tracked is the fraction of time moving in each axis, multiplied by the flight speed (since
 * EntityActionCreativeFlight currently just takes the velocity to add).
 */
public class CreativeFlightAccumulator
{
	private final CommonClientWorldCache _worldCache;
	private final float _flightBlocksPerSecond;

	private long _accumulationMillis;
	private long _lastSampleMillis;
	private float _accumulatedX;
	private float _accumulatedY;
	private float _accumulatedZ;
	private byte _newYaw;
	private byte _newPitch;
	private IEntitySubAction<IMutablePlayerEntity> _subAction;
	private Entity _lastNotifiedEntity;

	public CreativeFlightAccumulator(CommonClientWorldCache worldCache
		, long currentTimeMillis
	)
	{
		_worldCache = worldCache;
		_flightBlocksPerSecond = 2.0f * worldCache.env.creatures.PLAYER.blocksPerSecond();
		
		_lastSampleMillis = currentTimeMillis;
	}

	/**
	 * Sets the orientation.  Can be called at any time and only impacts the orientation sent in the next update.
	 * 
	 * @param yaw The left-right yaw.
	 * @param pitch The up-down pitch.
	 */
	public void setOrientation(byte yaw, byte pitch)
	{
		// This is applied to the current accumulation, immediately, as it is considered instant and saturating.
		_newYaw = yaw;
		_newPitch = pitch;
	}

	/**
	 * Hovers in place, passing time.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned) in order for accumulated motion to be sent to the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 * @return A completed change, if one was generated.
	 */
	public EntityActionCreativeFlight hover(long currentTimeMillis)
	{
		Assert.assertTrue(currentTimeMillis > 0L);
		
		EntityLocation vector = new EntityLocation(0.0f, 0.0f, 0.0f);
		EntityActionCreativeFlight toReturn = _commonMovement(currentTimeMillis, vector);
		return toReturn;
	}

	/**
	 * Flies in the given relative direction, potentially with change in elevation.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned) in order for accumulated motion to be sent to the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 * @param relativeDirection Movement, relative to the current yaw direction, or null if not moving horizontally.
	 * @param elevationChange The vertical direction to change.
	 * @return A completed change, if one was generated.
	 */
	public EntityActionCreativeFlight fly(long currentTimeMillis, RelativeDirection relativeDirection, VerticalDirection elevationChange)
	{
		Assert.assertTrue(currentTimeMillis > 0L);
		Assert.assertTrue(null != elevationChange);
		
		EntityLocation vector = _buildNormalizedVector(_newYaw, relativeDirection, elevationChange);
		EntityActionCreativeFlight toReturn = _commonMovement(currentTimeMillis, vector);
		return toReturn;
	}

	/**
	 * Enqueues a sub-action for the beginning of the next packed action.  This can fail if there is already an enqueued
	 * sub-action.
	 * 
	 * @param currentTimeMillis The current time.
	 * @param subAction The sub-action to enqueue.
	 * @return True if the action was enqueued, false if there is already one waiting.
	 */
	public boolean enqueueSubAction(long currentTimeMillis, IEntitySubAction<IMutablePlayerEntity> subAction)
	{
		// We will always just apply this immediately, unless there is already something there.
		boolean didApply = false;
		if (null == _subAction)
		{
			_subAction = subAction;
			EntityActionCreativeFlight toRun = _moveFromAccumulation();
			long millisToApply = _accumulationMillis;
			if (0L == millisToApply)
			{
				// We need to apply at least some time, just to see if this will work.
				millisToApply = 1L;
			}
			Entity newEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
			if (null == newEntity)
			{
				_subAction = null;
			}
			else
			{
				didApply = true;
			}
		}
		return didApply;
	}

	/**
	 * Called after any changes for this same time interval have been made and AFTER any returned action has been
	 * applied to the SpeculativeProjection.
	 * This method will apply any pending changes locally, updating the listener.
	 * 
	 * @return Returns whether or no a change was observed in the local accumulation and notified.
	 */
	public boolean applyLocalAccumulation()
	{
		_lastNotifiedEntity = _getLatestLocalEntity(_lastSampleMillis);
		if (_worldCache.thisEntity == _lastNotifiedEntity)
		{
			_lastNotifiedEntity = null;
		}
		else
		{
			_worldCache.listener.thisEntityDidChange(_lastNotifiedEntity);
		}
		return (null != _lastNotifiedEntity);
	}

	/**
	 * Clears all accumulated state, ready to begin building a new packed action with the next call.  This is useful if
	 * the client determines any of its projections are invalid in order to reset to a known good state.
	 */
	public void clearAccumulation()
	{
		_discardAccumulation(_lastSampleMillis);
	}

	/**
	 * Used to access the last accumulated Entity state kept internally.  Returns null if there is no accumulation.
	 * 
	 * @return The last Entity state sent via notification or null, if there is no accumulation.
	 */
	public Entity getLocalAccumulatedEntity()
	{
		return (_accumulationMillis > 0L)
			? _lastNotifiedEntity
			: null
		;
	}


	private EntityActionCreativeFlight _commonMovement(long currentTimeMillis, EntityLocation vector)
	{
		long toAdd = (currentTimeMillis - _lastSampleMillis);
		long combined = _accumulationMillis + toAdd;
		EntityActionCreativeFlight toReturn;
		if (combined > _worldCache.millisPerTick)
		{
			// Add to the existing, finish it, and start the next.
			long spillToNext = combined % _worldCache.millisPerTick;
			long addToExisting = (toAdd - spillToNext) % _worldCache.millisPerTick;
			if (0L == addToExisting)
			{
				addToExisting = _worldCache.millisPerTick;
			}
			
			_accumulateMovement(addToExisting, vector);
			toReturn = _mutativeTotalMoveFromAccumulation(currentTimeMillis);
			_discardAccumulation(currentTimeMillis);
			
			// Build what spilled into the next.
			_accumulateMovement(spillToNext, vector);
		}
		else if (combined == _worldCache.millisPerTick)
		{
			// Add to the existing, finish it, and clear state.
			_accumulateMovement(toAdd, vector);
			toReturn = _mutativeTotalMoveFromAccumulation(currentTimeMillis);
			_discardAccumulation(currentTimeMillis);
		}
		else
		{
			// Just add to the existing, returning null.
			_accumulateMovement(toAdd, vector);
			toReturn = null;
		}
		_lastSampleMillis = currentTimeMillis;
		return toReturn;
	}

	private void _accumulateMovement(long millisToAdd, EntityLocation vector)
	{
		// This vector is the velocity we want to set so scale it by the fraction of the tick we are adding and accumulate.
		float fraction = (float)millisToAdd / (float)_worldCache.millisPerTick;
		float magnitude = fraction * _flightBlocksPerSecond;
		_accumulationMillis += millisToAdd;
		_accumulatedX += magnitude * vector.x();
		_accumulatedY += magnitude * vector.y();
		_accumulatedZ += magnitude * vector.z();
	}

	private static EntityLocation _buildNormalizedVector(byte yaw, RelativeDirection relativeDirection, VerticalDirection elevationChange)
	{
		// The relative direction is allowed to be null so we just take the elevation change directly, in that case.
		EntityLocation normalized;
		if (null != relativeDirection)
		{
			float orientationRadians = OrientationHelpers.getYawRadians(yaw);
			float yawRadians = orientationRadians + relativeDirection.yawRadians;
			float xComponent = OrientationHelpers.getEastYawComponent(yawRadians);
			float yComponent = OrientationHelpers.getNorthYawComponent(yawRadians);
			EntityLocation rawVector = new EntityLocation(xComponent, yComponent, elevationChange.z);
			normalized = rawVector.makeScaledInstance(1.0f / rawVector.getMagnitude());
		}
		else
		{
			normalized = new EntityLocation(0.0f, 0.0f, elevationChange.z);
		}
		return normalized;
	}

	// Similar to _moveFromAccumulation() but will return null instead of a change which does nothing to the entity or world.
	private EntityActionCreativeFlight _mutativeTotalMoveFromAccumulation(long currentTimeMillis)
	{
		EntityActionCreativeFlight toRun = _moveFromAccumulation();
		if (null == _subAction)
		{
			// We always return actions with sub-actions so check those without to see if they can be dropped.
			long millisToApply = _accumulationMillis;
			Entity newEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
			if (newEntity == _worldCache.thisEntity)
			{
				// Nothing has changed so we can omit this and return nothing.
				toRun = null;
			}
		}
		return toRun;
	}

	private EntityActionCreativeFlight _moveFromAccumulation()
	{
		EntityActionCreativeFlight action = new EntityActionCreativeFlight(_accumulatedX
			, _accumulatedY
			, _accumulatedZ
			, _newYaw
			, _newPitch
			, _subAction
		);
		return action;
	}

	private void _discardAccumulation(long currentTimeMillis)
	{
		_accumulationMillis = 0L;
		_lastSampleMillis = currentTimeMillis;
		_accumulatedX = 0.0f;
		_accumulatedY = 0.0f;
		_accumulatedZ = 0.0f;
		_subAction = null;
	}

	private Entity _getLatestLocalEntity(long currentTimeMillis)
	{
		Entity entity;
		if (_accumulationMillis > 0L)
		{
			// Build one based on our local state.
			EntityActionCreativeFlight toRun = _moveFromAccumulation();
			long millisToApply = _accumulationMillis;
			Entity newEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
			if (null != newEntity)
			{
				entity = newEntity;
			}
			else
			{
				// There is something wrong so just reset.
				_discardAccumulation(currentTimeMillis);
				entity = _worldCache.thisEntity;
			}
		}
		else
		{
			// Default to the one we last received.
			entity = _worldCache.thisEntity;
		}
		return entity;
	}
}
