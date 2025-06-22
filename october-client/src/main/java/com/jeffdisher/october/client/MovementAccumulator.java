package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.EntityMovementHelpers;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.logic.MotionHelpers;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
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
import com.jeffdisher.october.types.TickProcessingContext;
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
	/**
	 * When applying the "coasting" deceleration at the beginning of a tick, we assume that any speed below this is "0".
	 */
	public static final float MIN_COASTING_SPEED = 0.5f;


	private final Environment _env;
	private final IProjectionListener _listener;
	private final long _millisPerTick;
	private final EntityVolume _playerVolume;

	private Entity _thisEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _world;
	private final Map<CuboidAddress, CuboidHeightMap> _heights;
	private final Map<Integer, PartialEntity> _otherEntities;
	private final Function<AbsoluteLocation, BlockProxy> _proxyLookup;

	private float _baselineZVector;
	private long _accumulationMillis;
	private long _lastSampleMillis;
	private float _startInverseViscosity;
	private EntityLocation _newLocation;
	private EntityLocation _newVelocity;
	private byte _newYaw;
	private byte _newPitch;
	private IMutationEntity<IMutablePlayerEntity> _subAction;
	private EntityChangeTopLevelMovement.Intensity _intensity;
	private boolean _didNotifyWorldChanges;

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
		_intensity = EntityChangeTopLevelMovement.Intensity.STANDING;
		
		_proxyLookup = (AbsoluteLocation location) -> {
			IReadOnlyCuboidData cuboid = _world.get(location.getCuboidAddress());
			return (null != cuboid)
					? new BlockProxy(location.getBlockAddress(), cuboid)
					: null
			;
		};
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
		// While we usually initialize in local apply of changes, sometimes (especially initially), we should initialize in the motion path.
		if (0L == _accumulationMillis)
		{
			_startInverseViscosity = 1.0f - EntityMovementHelpers.maxViscosityInEntityBlocks(_newLocation, _playerVolume, _proxyLookup);
		}
		
		// We only need to account for the existing velocity vector but otherwise just passing time.
		float xVelocity = _newVelocity.x();
		float yVelocity = _newVelocity.y();
		// In this case, don't apply any viscosity and just coast.
		return _commonAccumulateMotion(currentTimeMillis, xVelocity, yVelocity, 1.0f);
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
		// While we usually initialize in local apply of changes, sometimes (especially initially), we should initialize in the motion path.
		if (0L == _accumulationMillis)
		{
			_startInverseViscosity = 1.0f - EntityMovementHelpers.maxViscosityInEntityBlocks(_newLocation, _playerVolume, _proxyLookup);
		}
		
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
		
		// Whatever has happened so far, we need to bill this as walking.
		_intensity = EntityChangeTopLevelMovement.Intensity.WALKING;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = _commonAccumulateMotion(currentTimeMillis, xVelocity, yVelocity, _startInverseViscosity);
		if (_queuedMillis > 0L)
		{
			// If there was any overflow from this accumulation, we need to re-assert that it is walking.
			_intensity = EntityChangeTopLevelMovement.Intensity.WALKING;
		}
		return toReturn;
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
			_didNotifyWorldChanges = false;
			if (null != _queuedSubAction)
			{
				_runActionAndNotify(currentTimeMillis, _queuedSubAction, (Entity changedEntity) -> {
					_newLocation = changedEntity.location();
					_newVelocity = changedEntity.velocity();
					_listener.thisEntityDidChange(changedEntity, changedEntity);
				});
				_subAction = _queuedSubAction;
			}
			_startInverseViscosity = 1.0f - EntityMovementHelpers.maxViscosityInEntityBlocks(_newLocation, _playerVolume, _proxyLookup);
			_baselineZVector = _newVelocity.z();
			
			// We want to apply a sort of "drag" on the velocity when the tick starts so do that here to both X/Y (Z is handled differently since it isn't actively applied by the user).
			float newXVelocity = _startInverseViscosity * _newVelocity.x();
			if (Math.abs(newXVelocity) < MIN_COASTING_SPEED)
			{
				newXVelocity = 0.0f;
			}
			float newYVelocity = _startInverseViscosity * _newVelocity.y();
			if (Math.abs(newYVelocity) < MIN_COASTING_SPEED)
			{
				newXVelocity = 0.0f;
			}
			_newVelocity = new EntityLocation(newXVelocity, newYVelocity, _newVelocity.z());
			
			if (_queuedMillis > 0L)
			{
				_updateVelocityAndLocation(_queuedMillis, _startInverseViscosity * _queuedXVector, _startInverseViscosity * _queuedYVector);
			}
			_accumulationMillis = _queuedMillis;
			
			_queuedMillis = 0L;
			_queuedXVector = 0.0f;
			_queuedYVector = 0.0f;
			_queuedSubAction = null;
		}
		if (_accumulationMillis > 0L)
		{
			EntityChangeTopLevelMovement<IMutablePlayerEntity> toRun = new EntityChangeTopLevelMovement<>(_newLocation
				, _newVelocity
				, _intensity
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


	private EntityChangeTopLevelMovement<IMutablePlayerEntity> _buildFromAccumulation()
	{
		// If we are standing on solid blocks, we want to cancel all velocity (we currently assume all solid blocks have 100% friction).
		if (SpatialHelpers.isStandingOnGround(_proxyLookup, _newLocation, _playerVolume))
		{
			_newVelocity = new EntityLocation(0.0f
				, 0.0f
				, _newVelocity.z()
			);
		}
		
		// Note that we won't produce an action if there is no sub-action and nothing else has changed about the entity.
		// TODO:  Determine who owns energy level since this means that the client can't use energy while "doing nothing".
		EntityChangeTopLevelMovement<IMutablePlayerEntity> result;
		if ((null == _subAction)
			&& (_thisEntity.yaw() == _newYaw)
			&& (_thisEntity.pitch() == _newPitch)
			&& _thisEntity.location().equals(_newLocation)
			&& _thisEntity.velocity().equals(_newVelocity)
		)
		{
			// We can safely "do nothing".  This mostly to allow the server to "catch up" in case we are out of sync.
			result = null;
		}
		else
		{
			result = new EntityChangeTopLevelMovement<>(_newLocation
				, _newVelocity
				, _intensity
				, _newYaw
				, _newPitch
				, _subAction
				, _millisPerTick
			);
		}
		return result;
	}

	private void _clearAccumulation(long currentTimeMillis)
	{
		// This is called in the case where something went wrong and we should clear our accumulation back to its initial state.
		_accumulationMillis = 0L;
		_lastSampleMillis = currentTimeMillis;
		_newLocation = _thisEntity.location();
		_newVelocity = _thisEntity.velocity();
		_subAction = null;
		_startInverseViscosity = 0.0f;
		_baselineZVector = _newVelocity.z();
		_intensity = EntityChangeTopLevelMovement.Intensity.STANDING;
		
		_queuedMillis = 0L;
		_queuedXVector = 0.0f;
		_queuedYVector = 0.0f;
		_queuedSubAction = null;
	}

	private void _updateVelocityAndLocation(long millisToMove, float proposedVelocityX, float proposedVelocityY)
	{
		Environment env = Environment.getShared();
		float secondsToPass = (float)millisToMove / 1000.0f;
		_updateVelocityWithAccumulatedGravity(millisToMove);
		// For now, we will just use the proposed velocity for X/Y but we will later need some maximum acceleration for things like resisting knockback, etc.
		// TODO: Apply maximum velocity change constraint.
		// Z-velocity changes are only applied at the beginning of the tick so we will assume we can use whatever we were given.
		EntityLocation velocity = new EntityLocation(proposedVelocityX, proposedVelocityY, _newVelocity.z());
		EntityLocation effectiveMotion = new EntityLocation(secondsToPass * velocity.x(), secondsToPass * velocity.y(), secondsToPass * velocity.z());
		EntityMovementHelpers.interactiveEntityMove(_newLocation, _playerVolume, effectiveMotion, new EntityMovementHelpers.InteractiveHelper() {
			@Override
			public void setLocationAndViscosity(EntityLocation finalLocation, boolean cancelX, boolean cancelY, boolean cancelZ)
			{
				_newLocation = finalLocation;
				// We keep the velocity we proposed, except for any axes which were cancelled due to collision.
				_newVelocity = new EntityLocation(cancelX ? 0.0f : velocity.x()
					, cancelY ? 0.0f : velocity.y()
					, cancelZ ? 0.0f : velocity.z()
				);
			}
			@Override
			public float getViscosityForBlockAtLocation(AbsoluteLocation location)
			{
				BlockProxy proxy = _proxyLookup.apply(location);
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
	}

	private EntityChangeTopLevelMovement<IMutablePlayerEntity> _commonAccumulateMotion(long currentTimeMillis, float xVelocity, float yVelocity, float inverseViscosityMultiplier)
	{
		// First, see if we will need to split this time interval.
		long millisToAdd = (currentTimeMillis - _lastSampleMillis);
		long millisRemainingInAction = _millisPerTick - _accumulationMillis;
		if (millisToAdd >= _millisPerTick)
		{
			// If this was an overflow by more than a whole action increment, then just fill out the remaining unit.
			millisToAdd = millisRemainingInAction;
		}
		long accumulated = _accumulationMillis + millisToAdd;
		long overflowMillis = accumulated - _millisPerTick;
		float effectiveXVelocity = inverseViscosityMultiplier * xVelocity;
		float effectiveYVelocity = inverseViscosityMultiplier * yVelocity;
		
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn;
		if (overflowMillis >= 0L)
		{
			// We need to carve off the existing accumulation.
			long fillMillis = millisToAdd - overflowMillis;
			_updateVelocityAndLocation(fillMillis, effectiveXVelocity, effectiveYVelocity);
			toReturn = _buildFromAccumulation();
			
			// We now need to reset our counters.
			_intensity = EntityChangeTopLevelMovement.Intensity.STANDING;
			_queuedMillis = overflowMillis;
			_queuedXVector = xVelocity;
			_queuedYVector = yVelocity;
			_accumulationMillis = 0L;
		}
		else
		{
			// The accumulation is not yet finished so just accumulate.
			_updateVelocityAndLocation(millisToAdd, effectiveXVelocity, effectiveYVelocity);
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
		TickProcessingContext.IEventSink eventSink = (EventRecord event) -> {
			// TODO:  Come up with a way to relay these events without duplication in SpeculativeProjection since we likely need them immediately.
		};
		OneOffRunner.StatePackage output = OneOffRunner.runOneChange(input, eventSink, _millisPerTick, currentTimeMillis, toRun);
		if (null != output)
		{
			// This was a success so send off listener updates.
			if (_thisEntity != output.thisEntity())
			{
				changedEntityConsumer.accept(output.thisEntity());
			}
			
			// We only send any world updates at the beginning of the tick.
			if (!_didNotifyWorldChanges)
			{
				Map<CuboidAddress, CuboidHeightMap> localHeights = new HashMap<>(_heights);
				localHeights.putAll(output.heights());
				// Rebuild the height maps for only what columns changed.
				Set<CuboidColumnAddress> blockChangeCuboids = output.optionalBlockChanges().keySet().stream()
					.map((CuboidAddress address) -> address.getColumn())
					.collect(Collectors.toSet())
				;
				Map<CuboidAddress, CuboidHeightMap> changedCuboidMaps = localHeights.keySet().stream()
					.filter((CuboidAddress address) -> blockChangeCuboids.contains(address.getColumn()))
					.collect(Collectors.toMap(
						(CuboidAddress address) -> address, (CuboidAddress address) -> localHeights.get(address)
					))
				;
				Map<CuboidColumnAddress, ColumnHeightMap> columnHeightMaps = HeightMapHelpers.buildColumnMaps(changedCuboidMaps);
				for (Map.Entry<CuboidAddress, List<BlockChangeDescription>> entry : output.optionalBlockChanges().entrySet())
				{
					CuboidAddress address = entry.getKey();
					IReadOnlyCuboidData readOnly = _world.get(address);
					CuboidData cuboid = CuboidData.mutableClone(readOnly);
					Set<BlockAddress> changedBlocks = new HashSet<>();
					Set<Aspect<?, ?>> changedAspects = new HashSet<>();
					for (BlockChangeDescription desc : entry.getValue())
					{
						MutationBlockSetBlock setBlock = desc.serializedForm();
						setBlock.applyState(cuboid);
						Set<Aspect<?, ?>> changes = setBlock.changedAspectsVersusCuboid(readOnly);
						changedBlocks.add(setBlock.getAbsoluteLocation().getBlockAddress());
						changedAspects.addAll(changes);
					}
					// Don't want to write-back to _world, since that could cause conflicts on the next accumulation but we will send it to the listener.
					if (!changedAspects.isEmpty())
					{
						ColumnHeightMap height = columnHeightMaps.get(address.getColumn());
						_listener.cuboidDidChange(cuboid, localHeights.get(address), height, changedBlocks, changedAspects);
					}
				}
				_didNotifyWorldChanges = true;
			}
		}
		else
		{
			// We should reset accumulated changes since they are invalid.
			_clearAccumulation(currentTimeMillis);
		}
	}

	private void _updateVelocityWithAccumulatedGravity(long additionalMillis)
	{
		float secondsToPass = (float)(_accumulationMillis + additionalMillis) / 1000.0f;
		float zVelocityChange = secondsToPass * _startInverseViscosity * MotionHelpers.GRAVITY_CHANGE_PER_SECOND;
		float newZVelocity = _baselineZVector + zVelocityChange;
		float effectiveTerminalVelocity = _startInverseViscosity * MotionHelpers.FALLING_TERMINAL_VELOCITY_PER_SECOND;
		if (newZVelocity < effectiveTerminalVelocity)
		{
			newZVelocity = effectiveTerminalVelocity;
		}
		_newVelocity = new EntityLocation(_newVelocity.x(), _newVelocity.y(), newZVelocity);
	}
}
