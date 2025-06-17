package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Used to create EntityChangeTopLevelMovement instances by packing per-frame changes and special actions into changes
 * to submit to the SpeculativeProjection and then send to the server.
 * It is expected that the caller (typically ClientRunner) uses this in order to determine what changes are even valid
 * by connecting movement actions directly, allowing this implementation to determine if movements are colliding or
 * otherwise invalid.
 * This ownership of responsibilities allows the packing to have more complete visibility into the intent or high-level
 * meaning of an action, such that it can more correctly apply it, and also allows for this somewhat-complicated
 * movement logic to be tested in isolation.
 * The caller is still responsible for making calls in the correct order, applying any packed actions, and populating
 * its view based on what happens in a sibling SpeculativeProjection.
 */
public class MovementAccumulator
{
	private final Environment _env;
	private final IProjectionListener _listener;
	private final long _millisPerTick;
	private final EntityVolume _playerVolume;

	private Entity _thisEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _world;
	private final Map<CuboidAddress, CuboidHeightMap> _heights;
	private final Map<Integer, PartialEntity> _otherEntities;

	private long _accumulationMillis;
	private long _lastSampleMillis;
	private float _maxTickViscosity;
	private EntityLocation _newLocation;
	private EntityLocation _newVelocity;
	private byte _newYaw;
	private byte _newPitch;
	private IMutationEntity<IMutablePlayerEntity> _subAction;

	// Some information is queued for the "next" accumulation after this current action has been returned.
	private long _queuedMillis;
	private float _queuedXVector;
	private float _queuedYVector;
	private IMutationEntity<IMutablePlayerEntity> _queuedSubAction;

	public MovementAccumulator(IProjectionListener listener
		, long millisPerTick
		, EntityVolume playerVolume
		, long currentTimeMillis
	)
	{
		_env = Environment.getShared();
		_listener = listener;
		_millisPerTick = millisPerTick;
		_playerVolume = playerVolume;
		
		_world = new HashMap<>();
		_heights = new HashMap<>();
		_otherEntities = new HashMap<>();
		
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
		// applyLocalAccumulation() must be called to drain the queued information.
		Assert.assertTrue(0L == _queuedMillis);
		
		// This is applied to the current accumulation, immediately, as it is considered instant and saturating.
		_newYaw = yaw;
		_newPitch = pitch;
	}

	/**
	 * Takes no action, effectively just passing time.  This allows existing movement to just continue.
	 * 
	 * @param currentTimeMillis The current time.
	 * @return A completed change, if one was generated.
	 */
	public EntityChangeTopLevelMovement<IMutablePlayerEntity> stand(long currentTimeMillis)
	{
		// applyLocalAccumulation() must be called to drain the queued information.
		Assert.assertTrue(0L == _queuedMillis);
		
		// We only need to account for the existing velocity vector but otherwise just passing time.
		float xVelocity = _newVelocity.x();
		float yVelocity = _newVelocity.y();
		return _commonAccumulateMotion(currentTimeMillis, xVelocity, yVelocity);
	}

	/**
	 * Moves in the given relativeDirection up until the given time.
	 * NOTE:  applyLocalAccumulation() MUST be called after applying the returned action to SpeculativeProjection (or
	 * called directly, if null was returned).
	 * 
	 * @param currentTimeMillis The current time.
	 * @param relativeDirection Movement, relative to the current yaw direction.
	 * @return A completed change, if one was generated.
	 */
	public EntityChangeTopLevelMovement<IMutablePlayerEntity> move(long currentTimeMillis, EntityChangeTopLevelMovement.Relative relativeDirection)
	{
		// applyLocalAccumulation() must be called to drain the queued information.
		Assert.assertTrue(0L == _queuedMillis);
		
		// This is the same as standing, except that we override the X/Y velocity vectors based on the type of movement.
		float orientationRadians = OrientationHelpers.getYawRadians(_newYaw);
		float yawRadians = orientationRadians + relativeDirection.yawRadians;
		float xComponent = OrientationHelpers.getEastYawComponent(yawRadians);
		float yComponent = OrientationHelpers.getNorthYawComponent(yawRadians);
		
		// Determine the X/Y velocity based on these components and the walking type.
		float maxSpeed = _env.creatures.PLAYER.blocksPerSecond();
		float speed = maxSpeed * relativeDirection.speedMultiplier;
		float xVelocity = speed * xComponent;
		float yVelocity = speed * yComponent;
		return _commonAccumulateMotion(currentTimeMillis, xVelocity, yVelocity);
	}

	/**
	 * Enqueues a sub-action for the beginning of the next packed action.  This can fail if there is already an enqueued
	 * sub-action.
	 * 
	 * @param subAction The sub-action to enqueue.
	 * @return True if the action was enqueued, false if there is already one waiting.
	 */
	public boolean enqueueSubAction(IMutationEntity<IMutablePlayerEntity> subAction)
	{
		// applyLocalAccumulation() must be called to drain the queued information.
		Assert.assertTrue(0L == _queuedMillis);
		
		// This is applied directly to the current accumulation, unless there already is a sub-action.
		boolean didApply = false;
		if (null == _queuedSubAction)
		{
			_queuedSubAction = subAction;
			didApply = true;
		}
		return didApply;
	}

	/**
	 * Called after any changes for this same time interval have been made and AFTER any returned action has been
	 * applied to the SpeculativeProjection.
	 * This method will apply any pending changes locally, updating the listener.
	 * 
	 * @param currentTimeMillis The current time.
	 */
	public void applyLocalAccumulation(long currentTimeMillis)
	{
		// This is called after any updates are made to the external SpeculativeProjection to apply local accumulation on top of that.
		// Fist, we need to see if anything is queued up.
		if (0L == _accumulationMillis)
		{
			// If there was an action queued, apply it and store it.
			_subAction = null;
			_maxTickViscosity = 0.0f;
			if (null != _queuedSubAction)
			{
				_runActionAndNotify(currentTimeMillis, _queuedSubAction, (Entity changedEntity) -> {
					_newLocation = changedEntity.location();
					_newVelocity = changedEntity.velocity();
					_listener.thisEntityDidChange(changedEntity, changedEntity);
				});
				_subAction = _queuedSubAction;
			}
			
			// Apply the beginning of tick z-velocity change for gravity (done after the sub-action since it sets the velocity).
			_applyFallingGravity();
			
			if (_queuedMillis > 0L)
			{
				_updateVelocityAndLocation(_queuedMillis, _queuedXVector, _queuedYVector);
			}
			_accumulationMillis = _queuedMillis;
			
			_queuedMillis = 0L;
			_queuedXVector = 0.0f;
			_queuedYVector = 0.0f;
			_queuedSubAction = null;
		}
		if (_accumulationMillis > 0L)
		{
			EntityChangeTopLevelMovement.Intensity intensity = _findActionIntensity(_newLocation);
			EntityChangeTopLevelMovement<IMutablePlayerEntity> toRun = new EntityChangeTopLevelMovement<>(_newLocation
				, _newVelocity
				, intensity
				, _newYaw
				, _newPitch
				, _subAction
				, _accumulationMillis
			);
			_runActionAndNotify(currentTimeMillis, toRun, (Entity changedEntity) -> {
				_listener.thisEntityDidChange(changedEntity, changedEntity);
			});
		}
	}

	/**
	 * Clears all accumulated state, ready to begin building a new packed action with the next call.  This is useful if
	 * the client determines any of its projections are invalid in order to reset to a known good state.
	 * 
	 * @param currentTimeMillis The current time.
	 */
	public void clearAccumulation(long currentTimeMillis)
	{
		_clearAccumulation(currentTimeMillis);
		// Since this is called when are starting a tick (either at the beginning or after an error, apply gravity).
		_applyFallingGravity();
	}

	/**
	 * Sets the underlying "known good" entity state for this entity.  Does not immediately impact accumulated state.
	 * 
	 * @param entity The new entity state.
	 */
	public void setThisEntity(Entity entity)
	{
		_thisEntity = entity;
	}

	/**
	 * Sets or updates a cuboid in the local reference state.
	 * 
	 * @param cuboid The cuboid.
	 * @param heightMap The height map for the cuboid.
	 */
	public void setCuboid(IReadOnlyCuboidData cuboid, CuboidHeightMap heightMap)
	{
		CuboidAddress address = cuboid.getCuboidAddress();
		_world.put(address, cuboid);
		_heights.put(address, heightMap);
	}

	/**
	 * Removes the cuboid with the given address from local reference state.
	 * 
	 * @param address The address of the cuboid to remove.
	 */
	public void removeCuboid(CuboidAddress address)
	{
		Assert.assertTrue(null != _world.remove(address));
		Assert.assertTrue(null != _heights.remove(address));
	}

	/**
	 * Sets or updates another entity in the local reference state.
	 * 
	 * @param entity The entity to store.
	 */
	public void setOtherEntity(PartialEntity entity)
	{
		int id = entity.id();
		_otherEntities.put(id, entity);
	}

	/**
	 * Removes another entity from the local reference state.
	 * 
	 * @param id The ID of the entity to remove.
	 */
	public void removeOtherEntity(int id)
	{
		Assert.assertTrue(null != _otherEntities.remove(id));
	}


	private EntityChangeTopLevelMovement.Intensity _findActionIntensity(EntityLocation location)
	{
		// For now, we will just look at x/y movement (this might not be ideal if coasting in the air).
		EntityLocation knownLocation = _thisEntity.location();
		EntityChangeTopLevelMovement.Intensity intensity = ((location.y() == knownLocation.y()) && (location.x() == knownLocation.x()))
			? EntityChangeTopLevelMovement.Intensity.STANDING
			: EntityChangeTopLevelMovement.Intensity.WALKING
		;
		return intensity;
	}

	private EntityChangeTopLevelMovement<IMutablePlayerEntity> _buildFromAccumulation()
	{
		// For now, we will determine if we are standing or walking based on whether or not we moved horizontally.
		EntityChangeTopLevelMovement.Intensity intensity = _findActionIntensity(_newLocation);
		
		// We will apply viscosity to the velocity (this will cause quick, but not immediate, deceleration when falling into water, etc).
		float velocityMultiplier = 1.0f - _maxTickViscosity;
		_newVelocity = new EntityLocation(_newVelocity.x() * velocityMultiplier
			, _newVelocity.y() * velocityMultiplier
			, _newVelocity.z() * velocityMultiplier
		);
		
		// Now, create the finished top-level action.
		return new EntityChangeTopLevelMovement<>(_newLocation
			, _newVelocity
			, intensity
			, _newYaw
			, _newPitch
			, _subAction
			, _millisPerTick
		);
	}

	private void _clearAccumulation(long currentTimeMillis)
	{
		// This is called in the case where something went wrong and we should clear our accumulation back to its initial state.
		_accumulationMillis = 0L;
		_lastSampleMillis = currentTimeMillis;
		_newLocation = _thisEntity.location();
		_newVelocity = _thisEntity.velocity();
		_subAction = null;
		_maxTickViscosity = 0.0f;
		
		_queuedMillis = 0L;
		_queuedXVector = 0.0f;
		_queuedYVector = 0.0f;
		_queuedSubAction = null;
	}

	private void _updateVelocityAndLocation(long millisToMove, float proposedVelocityX, float proposedVelocityY)
	{
		Environment env = Environment.getShared();
		float secondsToPass = (float)millisToMove / 1000.0f;
		// For now, we will just use the proposed velocity for X/Y but we will later need some maximum acceleration for things like resisting knockback, etc.
		// TODO: Apply maximum velocity change constraint.
		// Z-velocity changes are only applied at the beginning of the tick so we will assume we can use whatever we were given.
		EntityLocation velocity = new EntityLocation(proposedVelocityX, proposedVelocityY, _newVelocity.z());
		EntityLocation effectiveMotion = new EntityLocation(secondsToPass * velocity.x(), secondsToPass * velocity.y(), secondsToPass * velocity.z());
		EntityMovementHelpers.interactiveEntityMove(_newLocation, _playerVolume, effectiveMotion, new EntityMovementHelpers.InteractiveHelper() {
			@Override
			public void setLocationAndViscosity(EntityLocation finalLocation, float viscosity, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				_newLocation = finalLocation;
				// We keep the velocity we proposed, except for any axes which were cancelled due to collision.
				_newVelocity = new EntityLocation(cancelX ? 0.0f : velocity.x()
					, cancelY ? 0.0f : velocity.y()
					, cancelZ ? 0.0f : velocity.z()
				);
				// Since this can be called for very small slices of time, we only want to consider the viscosity for deceleration at the end of a tick.
				_maxTickViscosity = Math.max(_maxTickViscosity, viscosity);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
			{
				IReadOnlyCuboidData cuboid = _world.get(location.getCuboidAddress());
				float viscosity;
				if (null != cuboid)
				{
					// Find the viscosity based on block type.
					BlockProxy proxy = new BlockProxy(location.getBlockAddress(), cuboid);
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
	}

	private EntityChangeTopLevelMovement<IMutablePlayerEntity> _commonAccumulateMotion(long currentTimeMillis, float xVelocity, float yVelocity)
	{
		// First, see if we will need to split this time interval.
		long millisToAdd = (currentTimeMillis - _lastSampleMillis) % _millisPerTick;
		long accumulated = _accumulationMillis + millisToAdd;
		long overflowMillis = accumulated - _millisPerTick;
		
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn;
		if (overflowMillis >= 0L)
		{
			// We need to carve off the existing accumulation.
			long fillMillis = millisToAdd - overflowMillis;
			_updateVelocityAndLocation(fillMillis, xVelocity, yVelocity);
			toReturn = _buildFromAccumulation();
			_queuedMillis = overflowMillis;
			_queuedXVector = xVelocity;
			_queuedYVector = yVelocity;
			_accumulationMillis = 0L;
		}
		else
		{
			// The accumulation is not yet finished so just accumulate.
			_updateVelocityAndLocation(millisToAdd, xVelocity, yVelocity);
			_accumulationMillis = accumulated;
			toReturn = null;
		}
		_lastSampleMillis = currentTimeMillis;
		return toReturn;
	}

	private void _runActionAndNotify(long currentTimeMillis, IMutationEntity<IMutablePlayerEntity> toRun, Consumer<Entity> changedEntityConsumer)
	{
		OneOffRunner.StatePackage input = new OneOffRunner.StatePackage(_thisEntity
			, _world
			, _heights
			, null
			, _otherEntities
		);
		OneOffRunner.StatePackage output = OneOffRunner.runOneChange(input, (EventRecord event) -> _listener.handleEvent(event), _millisPerTick, currentTimeMillis, toRun);
		if (null != output)
		{
			// This was a success so send off listener updates.
			if (_thisEntity != output.thisEntity())
			{
				changedEntityConsumer.accept(output.thisEntity());
			}
			Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = HeightMapHelpers.buildColumnMaps(output.heights());
			for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> entry : output.optionalBlockChanges().entrySet())
			{
				CuboidAddress address = entry.getKey();
				IReadOnlyCuboidData cuboid = _world.get(address);
				Set<BlockAddress> changedBlocks = new HashSet<>();
				Set<Aspect<?, ?>> changedAspects = new HashSet<>();
				for (BlockChangeDescription desc : entry.getValue())
				{
					MutationBlockSetBlock setBlock = desc.serializedForm();
					Set<Aspect<?, ?>> changes = setBlock.changedAspectsVersusCuboid(cuboid);
					changedBlocks.add(setBlock.getAbsoluteLocation().getBlockAddress());
					changedAspects.addAll(changes);
				}
				if (!changedAspects.isEmpty())
				{
					ColumnHeightMap height = columnHeightMaps.get(address.getColumn());
					_listener.cuboidDidChange(cuboid, height, changedBlocks, changedAspects);
				}
			}
		}
		else
		{
			// We should reset accumulated changes since they are invalid.
			_clearAccumulation(currentTimeMillis);
		}
	}

	private void _applyFallingGravity()
	{
		float secondsToPass = (float)_millisPerTick / 1000.0f;
		float zVelocityChange = secondsToPass * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
		float newZVelocity = _newVelocity.z() + zVelocityChange;
		if (newZVelocity < MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND)
		{
			newZVelocity = MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
		}
		_newVelocity = new EntityLocation(_newVelocity.x(), _newVelocity.y(), newZVelocity);
	}
}
