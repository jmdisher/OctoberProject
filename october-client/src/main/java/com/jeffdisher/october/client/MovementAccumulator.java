package com.jeffdisher.october.client;

import com.jeffdisher.october.actions.EntityActionSimpleMove;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.subactions.EntitySubActionPopOutOfBlock;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;


/**
 * Used to convert high-level per-frame activities into EntityActionSimpleMove instances which can be run on
 * SpeculativeProjection and sent to the server.
 * It is expected that the caller (typically ClientRunner) uses this in order to determine what changes are even valid
 * by connecting movement actions directly, allowing this implementation to determine if movements are colliding or
 * otherwise invalid.
 * This ownership of responsibilities allows the packing to have more complete visibility into the intent or high-level
 * meaning of an action, such that it can more correctly apply it, and also allows for this somewhat-complicated
 * movement logic to be tested in isolation.
 * The caller is still responsible for making calls in the correct order, applying any packed actions, and populating
 * its view based on what happens in a sibling SpeculativeProjection.
 * NOTE:  This is also the component which injects EntitySubActionPopOutOfBlock if it detects that the player is stuck.
 */
public class MovementAccumulator
{
	public static final float DEFAULT_SPEED_MULTIPLIER = 1.0f;
	public static final float SNEAK_SPEED_MULTIPLIER = 0.5f;
	public static final float STANDING_SPEED_MULTIPLIER = 0.0f;

	private final CommonClientWorldCache _worldCache;

	private long _accumulationMillis;
	private long _lastSampleMillis;
	private _Motion _accumulatedMotion;
	private byte _newYaw;
	private byte _newPitch;
	private IEntitySubAction<IMutablePlayerEntity> _subAction;
	private EntityActionSimpleMove.Intensity _intensity;
	private Entity _lastNotifiedEntity;

	public MovementAccumulator(CommonClientWorldCache worldCache
		, long currentTimeMillis
	)
	{
		_worldCache = worldCache;
		
		_discardAccumulation(currentTimeMillis);
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
	 * Takes no action, effectively just passing time.  This allows existing movement to just continue.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned) in order for accumulated motion to be sent to the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 * @return A completed change, if one was generated.
	 */
	public EntityActionSimpleMove<IMutablePlayerEntity> stand(long currentTimeMillis)
	{
		// We don't want to change motion here.
		RelativeDirection relativeDirection = RelativeDirection.FORWARD;
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity.STANDING;
		float speedMultiplier = STANDING_SPEED_MULTIPLIER;
		EntityActionSimpleMove<IMutablePlayerEntity> toReturn = _commonMovement(currentTimeMillis, relativeDirection, intensity, speedMultiplier);
		return toReturn;
	}

	/**
	 * Walks in the given relativeDirection up until the given time.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned) in order for accumulated motion to be sent to the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 * @param relativeDirection Movement, relative to the current yaw direction.
	 * @param runningSpeed True if we should run, instead of walk.
	 * @return A completed change, if one was generated.
	 */
	public EntityActionSimpleMove<IMutablePlayerEntity> walk(long currentTimeMillis, RelativeDirection relativeDirection, boolean runningSpeed)
	{
		// We will need to add in some movement here and potentially increase the stored intensity.
		EntityActionSimpleMove.Intensity intensity = runningSpeed
			? EntityActionSimpleMove.Intensity.RUNNING
			: EntityActionSimpleMove.Intensity.WALKING
		;
		float speedMultiplier = DEFAULT_SPEED_MULTIPLIER;
		EntityActionSimpleMove<IMutablePlayerEntity> toReturn = _commonMovement(currentTimeMillis, relativeDirection, intensity, speedMultiplier);
		return toReturn;
	}

	/**
	 * Walks in the given relativeDirection up until the given time, but using a sneaking walking style.  Sneaking means
	 * that they walk at half the speed, using the same energy as walking, but also won't slip off of a ledge.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned) in order for accumulated motion to be sent to the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 * @param relativeDirection Movement, relative to the current yaw direction.
	 * @return A completed change, if one was generated.
	 */
	public EntityActionSimpleMove<IMutablePlayerEntity> sneak(long currentTimeMillis, RelativeDirection relativeDirection)
	{
		// Sneaking is similar to walking but we need to check if it would cause us to go from solid ground to no longer
		// on solid ground and convert it into standing, in that case.
		
		// Before we apply anything, determine if we started on the ground.
		Entity currentEntity = _getLatestLocalEntity(currentTimeMillis);
		boolean startedOnGround = SpatialHelpers.isStandingOnGround(_worldCache.reader, currentEntity.location(), _worldCache.playerVolume);
		
		// These are the constant parameters for every path.
		EntityActionSimpleMove.Intensity intensity = EntityActionSimpleMove.Intensity.WALKING;
		float speedMultiplier = SNEAK_SPEED_MULTIPLIER;
		
		EntityActionSimpleMove<IMutablePlayerEntity> toReturn;
		if (!startedOnGround)
		{
			// In this case, we ignore the sneak and it is a normal walk.
			toReturn = _commonMovement(currentTimeMillis, relativeDirection, intensity, speedMultiplier);
		}
		else
		{
			// We will treat this as "all or nothing" so would we be on the ground if we made the complete move.
			long toAdd = (currentTimeMillis - _lastSampleMillis);
			_Motion step = _buildOneStep(toAdd
				, relativeDirection
				, intensity
				, speedMultiplier
			);
			_Motion combined = _Motion.sum(_accumulatedMotion, step);
			EntityActionSimpleMove.Intensity localIntensity = _saturateIntensity(intensity);
			EntityActionSimpleMove<IMutablePlayerEntity> toRun = new EntityActionSimpleMove<>(combined.moveX
				, combined.moveY
				, localIntensity
				, _newYaw
				, _newPitch
				, _subAction
			);
			long millisToApply = _accumulationMillis + toAdd;
			Entity possibleEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
			boolean endedOnGround = SpatialHelpers.isStandingOnGround(_worldCache.reader, possibleEntity.location(), _worldCache.playerVolume);
			
			if (endedOnGround)
			{
				// We can treat this like a normal walk, just with slow speed.
				toReturn = _commonMovement(currentTimeMillis, relativeDirection, intensity, speedMultiplier);
			}
			else
			{
				// This would push us off the edge so treat it like we are just standing still.
				toReturn = _commonMovement(currentTimeMillis, relativeDirection, intensity, STANDING_SPEED_MULTIPLIER);
			}
		}
		_lastSampleMillis = currentTimeMillis;
		return toReturn;
	}

	/**
	 * Enqueues a sub-action for the beginning of the next packed action.  This can fail if there is already an enqueued
	 * sub-action.
	 * 
	 * @param subAction The sub-action to enqueue.
	 * @param currentTimeMillis The current time.
	 * @return True if the action was enqueued, false if there is already one waiting.
	 */
	public boolean enqueueSubAction(IEntitySubAction<IMutablePlayerEntity> subAction, long currentTimeMillis)
	{
		// We will always just apply this immediately, unless there is already something there.
		boolean didApply = false;
		if (null == _subAction)
		{
			_subAction = subAction;
			EntityActionSimpleMove<IMutablePlayerEntity> toRun = _moveFromAccumulation();
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


	private _Motion _buildOneStep(long millisMoving
		, RelativeDirection relativeDirection
		, EntityActionSimpleMove.Intensity intensity
		, float speedMultiplier
	)
	{
		float orientationRadians = OrientationHelpers.getYawRadians(_newYaw);
		float yawRadians = orientationRadians + relativeDirection.yawRadians;
		float xComponent = OrientationHelpers.getEastYawComponent(yawRadians);
		float yComponent = OrientationHelpers.getNorthYawComponent(yawRadians);
		float maxSpeed = _worldCache.env.creatures.PLAYER.blocksPerSecond() * intensity.speedMultipler * speedMultiplier;
		float speed = maxSpeed * relativeDirection.speedMultiplier;
		float seconds = (float)millisMoving / 1000.0f;
		
		float moveX = xComponent * speed * seconds;
		float moveY = yComponent * speed * seconds;
		return new _Motion(moveX, moveY);
	}

	private EntityActionSimpleMove.Intensity _saturateIntensity(EntityActionSimpleMove.Intensity intensity)
	{
		EntityActionSimpleMove.Intensity saturated;
		if (intensity.energyCostPerTick > _intensity.energyCostPerTick)
		{
			saturated = intensity;
		}
		else
		{
			saturated = _intensity;
		}
		return saturated;
	}

	private Entity _getLatestLocalEntity(long currentTimeMillis)
	{
		Entity entity;
		if (_accumulationMillis > 0L)
		{
			// Build one based on our local state.
			EntityActionSimpleMove<IMutablePlayerEntity> toRun = _moveFromAccumulation();
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

	// Similar to _moveFromAccumulation() but will return null instead of a change which does nothing to the entity or world.
	private EntityActionSimpleMove<IMutablePlayerEntity> _mutativeTotalMoveFromAccumulation(long currentTimeMillis)
	{
		EntityActionSimpleMove<IMutablePlayerEntity> toRun = _moveFromAccumulation();
		if (null == _subAction)
		{
			// We always return actions with sub-actions so check those without to see if they can be dropped.
			long millisToApply = _accumulationMillis;
			Entity newEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
			if (newEntity == _worldCache.thisEntity)
			{
				// This changes nothing, so that either means that there is nothing changing, or that we are stuck in a block (we need to check for collision in either case since both can be true).
				EntityLocation targetLocation = EntityMovementHelpers.popOutLocation(_worldCache.reader, _worldCache.thisEntity.location(), _worldCache.playerVolume, EntitySubActionPopOutOfBlock.POP_OUT_MAX_DISTANCE);
				if (null != targetLocation)
				{
					_subAction = new EntitySubActionPopOutOfBlock<IMutablePlayerEntity>(targetLocation);
					toRun = _moveFromAccumulation();
					newEntity = _worldCache.localEntityAfterAction(toRun, millisToApply, currentTimeMillis);
					if ((null == newEntity) || (newEntity == _worldCache.thisEntity))
					{
						// This didn't work either.
						_subAction = null;
						toRun = null;
					}
				}
				else
				{
					// We can't pop out so just give up.
					toRun = null;
				}
			}
		}
		return toRun;
	}

	private EntityActionSimpleMove<IMutablePlayerEntity> _moveFromAccumulation()
	{
		return new EntityActionSimpleMove<>(_accumulatedMotion.moveX
			, _accumulatedMotion.moveY
			, _intensity
			, _newYaw
			, _newPitch
			, _subAction
		);
	}

	private void _discardAccumulation(long currentTimeMillis)
	{
		_accumulationMillis = 0L;
		_lastSampleMillis = currentTimeMillis;
		_accumulatedMotion = new _Motion(0.0f, 0.0f);
		_subAction = null;
		_intensity = EntityActionSimpleMove.Intensity.STANDING;
	}

	private void _accumulateMovement(long millis, RelativeDirection relativeDirection, EntityActionSimpleMove.Intensity intensity, float speedMultiplier)
	{
		_Motion step = _buildOneStep(millis
			, relativeDirection
			, intensity
			, speedMultiplier
		);
		_accumulationMillis += millis;
		_accumulatedMotion = _Motion.sum(_accumulatedMotion, step);
	}

	private EntityActionSimpleMove<IMutablePlayerEntity> _commonMovement(long currentTimeMillis
		, RelativeDirection relativeDirection
		, EntityActionSimpleMove.Intensity intensity
		, float speedMultiplier
	)
	{
		_intensity = _saturateIntensity(intensity);
		long toAdd = (currentTimeMillis - _lastSampleMillis);
		long combined = _accumulationMillis + toAdd;
		EntityActionSimpleMove<IMutablePlayerEntity> toReturn;
		if (combined > _worldCache.millisPerTick)
		{
			// Add to the existing, finish it, and start the next.
			long spillToNext = combined % _worldCache.millisPerTick;
			long addToExisting = (toAdd - spillToNext) % _worldCache.millisPerTick;
			if (0L == addToExisting)
			{
				addToExisting = _worldCache.millisPerTick;
			}
			
			_accumulateMovement(addToExisting, relativeDirection, intensity, speedMultiplier);
			toReturn = _mutativeTotalMoveFromAccumulation(currentTimeMillis);
			_discardAccumulation(currentTimeMillis);
			
			// Build what spilled into the next.
			_accumulationMillis = spillToNext;
			_accumulatedMotion = _buildOneStep(spillToNext
				, relativeDirection
				, intensity
				, speedMultiplier
			);
			_intensity = intensity;
		}
		else if (combined == _worldCache.millisPerTick)
		{
			// Add to the existing, finish it, and clear state.
			_accumulateMovement(toAdd, relativeDirection, intensity, speedMultiplier);
			toReturn = _mutativeTotalMoveFromAccumulation(currentTimeMillis);
			_discardAccumulation(currentTimeMillis);
		}
		else
		{
			// Just add to the existing, returning null.
			_accumulateMovement(toAdd, relativeDirection, intensity, speedMultiplier);
			toReturn = null;
		}
		_lastSampleMillis = currentTimeMillis;
		return toReturn;
	}


	private static record _Motion(float moveX, float moveY)
	{
		public static _Motion sum(_Motion one, _Motion two)
		{
			return new _Motion(one.moveX + two.moveX, one.moveY + two.moveY);
		}
	}
}
