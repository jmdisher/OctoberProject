package com.jeffdisher.october.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.BlockChangeDescription;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeTopLevelMovement;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationBlockSetBlock;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Used to create EntityChangeTopLevelMovement instances by packing per-frame changes and special actions into changes
 * to submit to the SpeculativeProjection and then send to the server.
 * Note that the implementation assumes that all changes being requested make sense within the client's view of the
 * state.  For example:  Walking isn't going to collide and they are jumping from the ground.
 */
public class MovementAccumulator
{
	private final IProjectionListener _listener;
	private final long _millisPerTick;

	private Entity _thisEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _world;
	private final Map<CuboidAddress, CuboidHeightMap> _heights;
	private final Map<Integer, PartialEntity> _otherEntities;

	private long _accumulationMillis;
	private long _lastSampleMillis;
	private EntityLocation _newLocation;
	private EntityLocation _newVelocity;
	private byte _newYaw;
	private byte _newPitch;
	private IMutationEntity<IMutablePlayerEntity> _subAction;

	public MovementAccumulator(IProjectionListener listener
		, long millisPerTick
		, long currentTimeMillis
	)
	{
		_listener = listener;
		_millisPerTick = millisPerTick;
		
		_world = new HashMap<>();
		_heights = new HashMap<>();
		_otherEntities = new HashMap<>();
		
		_lastSampleMillis = currentTimeMillis;
	}

	public EntityChangeTopLevelMovement<IMutablePlayerEntity> move(long currentTimeMillis, EntityLocation location, EntityLocation velocity, byte yaw, byte pitch, boolean isWalking)
	{
		// If this was more than a tick later, we will just assume that it was paused, or something, and shave off the extra time.
		long millisToAdd = (currentTimeMillis - _lastSampleMillis) % _millisPerTick;
		long accumulated = _accumulationMillis + millisToAdd;
		long overflowMillis = accumulated - _millisPerTick;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = null;
		if (overflowMillis >= 0L)
		{
			// We have enough movement to generate the change.
			EntityLocation locationToSend;
			EntityLocation velocityToSend;
			if (overflowMillis > 0L)
			{
				// We have over-shot the budget so we need to interpolate location and velocity.
				// NOTE:  We will start by assuming linear interpolation but this isn't right for location when accelerating (like falling).
				long millisInMove = millisToAdd - overflowMillis;
				float multiplier = (float)millisInMove / (float)millisToAdd;
				locationToSend = _interpolate(_newLocation, location, multiplier);
				velocityToSend = _interpolate(_newVelocity, velocity, multiplier);
			}
			else
			{
				// This fits so just send it off.
				locationToSend = location;
				velocityToSend = velocity;
			}
			EntityChangeTopLevelMovement.Intensity intensity = _findActionIntensity(location);
			toReturn = new EntityChangeTopLevelMovement<>(locationToSend
				, velocityToSend
				, intensity
				, yaw
				, pitch
				, _subAction
				, _millisPerTick
			);
			_subAction = null;
			_accumulationMillis = overflowMillis;
		}
		else
		{
			_accumulationMillis = accumulated;
		}
		_lastSampleMillis = currentTimeMillis;
		_newLocation = location;
		_newVelocity = velocity;
		_newYaw = yaw;
		_newPitch = pitch;
		return toReturn;
	}

	public EntityChangeTopLevelMovement<IMutablePlayerEntity> stand(long currentTimeMillis, byte yaw, byte pitch)
	{
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = _finishAccumulate(currentTimeMillis);
		_newYaw = yaw;
		_newPitch = pitch;
		return toReturn;
	}

	public EntityChangeTopLevelMovement<IMutablePlayerEntity> jump(long currentTimeMillis)
	{
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = _finishAccumulate(currentTimeMillis);
		if (null == _subAction)
		{
			// We can add the jump change here and adapt the velocity vector.
			_subAction = new EntityChangeJump<>();
			_newVelocity = new EntityLocation(_newVelocity.x(), _newVelocity.y(), EntityChangeJump.JUMP_FORCE);
		}
		return toReturn;
	}

	public EntityChangeTopLevelMovement<IMutablePlayerEntity> misc(long currentTimeMillis, IMutationEntity<IMutablePlayerEntity> subAction)
	{
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = _finishAccumulate(currentTimeMillis);
		if (null == _subAction)
		{
			// Just inject this action.
			_subAction = subAction;
		}
		return toReturn;
	}

	public void applyLocalAccumulation(long currentTimeMillis)
	{
		// This is called after any updates are made to the external SpeculativeProjection to apply local accumulation on top of that.
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
					_listener.thisEntityDidChange(output.thisEntity(), output.thisEntity());
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
				_resetAccumulation(currentTimeMillis);
			}
		}
	}

	public void resetAccumulation(long currentTimeMillis)
	{
		_resetAccumulation(currentTimeMillis);
	}

	public void setThisEntity(Entity entity)
	{
		_thisEntity = entity;
	}

	public void setCuboid(IReadOnlyCuboidData cuboid, CuboidHeightMap heightMap)
	{
		CuboidAddress address = cuboid.getCuboidAddress();
		_world.put(address, cuboid);
		_heights.put(address, heightMap);
	}

	public void removeCuboid(CuboidAddress address)
	{
		Assert.assertTrue(null != _world.remove(address));
		Assert.assertTrue(null != _heights.remove(address));
	}

	public void setOtherEntity(PartialEntity entity)
	{
		int id = entity.id();
		_otherEntities.put(id, entity);
	}

	public void removeOtherEntity(int id)
	{
		Assert.assertTrue(null != _otherEntities.remove(id));
	}


	private EntityLocation _interpolate(EntityLocation start, EntityLocation end, float multiplier)
	{
		float x = end.x() - start.x();
		float y = end.y() - start.y();
		float z = end.z() - start.z();
		return new EntityLocation(multiplier * x + start.x()
			, multiplier * y + start.y()
			, multiplier * z + start.z()
		);
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

	private EntityChangeTopLevelMovement<IMutablePlayerEntity> _finishAccumulate(long currentTimeMillis)
	{
		// If this was more than a tick later, we will just assume that it was paused, or something, and shave off the extra time.
		long millisToAdd = (currentTimeMillis - _lastSampleMillis) % _millisPerTick;
		long accumulated = _accumulationMillis + millisToAdd;
		long overflowMillis = accumulated - _millisPerTick;
		EntityChangeTopLevelMovement<IMutablePlayerEntity> toReturn = null;
		if (overflowMillis >= 0L)
		{
			// We have enough movement to generate the change.
			EntityChangeTopLevelMovement.Intensity intensity = _findActionIntensity(_newLocation);
			toReturn = new EntityChangeTopLevelMovement<>(_newLocation
				, _newVelocity
				, intensity
				, _newYaw
				, _newPitch
				, _subAction
				, _millisPerTick
			);
			_subAction = null;
			_accumulationMillis = overflowMillis;
		}
		else
		{
			_accumulationMillis = accumulated;
		}
		_lastSampleMillis = currentTimeMillis;
		return toReturn;
	}

	private void _resetAccumulation(long currentTimeMillis)
	{
		
	}
}
