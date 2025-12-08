package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Updates the partial entity by setting its whole state.
 */
public class PartialEntityUpdate
{
	public static PartialEntityUpdate deserializeFromNetworkBuffer(ByteBuffer buffer)
	{
		PartialEntity entity = CodecHelpers.readPartialEntity(buffer);
		return new PartialEntityUpdate(entity);
	}

	/**
	 * Checks if this change encoding would even show anything.  This is used in cases where the change to an entity
	 * might be in data not relevant for PartialEntity instances, meaning it can be skipped.
	 * 
	 * @param previousEntityVersion The version of the entity from the previous tick.
	 * @param currentEntityVersion The current version of the entity from this tick.
	 * @return True if there is a change between these which can be described.
	 */
	public static boolean canDescribeChange(Entity previousEntityVersion, Entity currentEntityVersion)
	{
		return (!previousEntityVersion.location().equals(currentEntityVersion.location()))
			|| (previousEntityVersion.yaw() != currentEntityVersion.yaw())
			|| (previousEntityVersion.pitch() != currentEntityVersion.pitch())
			|| (previousEntityVersion.health() != currentEntityVersion.health())
		;
	}

	/**
	 * Checks if this change encoding would even show anything.  This is used in cases where the change to a creature
	 * might be in data not relevant for PartialEntity instances, meaning it can be skipped.
	 * 
	 * @param previousCreatureVersion The version of the creature from the previous tick.
	 * @param currentCreatureVersion The current version of the creature from this tick.
	 * @return True if there is a change between these which can be described.
	 */
	public static boolean canDescribeCreatureChange(CreatureEntity previousCreatureVersion, CreatureEntity currentCreatureVersion)
	{
		return (!previousCreatureVersion.location().equals(currentCreatureVersion.location()))
			|| (previousCreatureVersion.yaw() != currentCreatureVersion.yaw())
			|| (previousCreatureVersion.pitch() != currentCreatureVersion.pitch())
			|| (previousCreatureVersion.health() != currentCreatureVersion.health())
			|| (previousCreatureVersion.type() != currentCreatureVersion.type())
			// Note that we will assume that a change in the instance means an actual change, since these should be copy-on-write.
			|| (previousCreatureVersion.extendedData() != currentCreatureVersion.extendedData())
		;
	}


	private final PartialEntity _entity;

	public PartialEntityUpdate(PartialEntity entity)
	{
		_entity = entity;
	}

	/**
	 * Applies the receiver to the given newEntity.
	 * 
	 * @param newEntity The partial entity which should be updated by the receiver.
	 */
	public void applyToEntity(MutablePartialEntity newEntity)
	{
		newEntity.newType = _entity.type();
		newEntity.newLocation = _entity.location();
		newEntity.newYaw = _entity.yaw();
		newEntity.newPitch = _entity.pitch();
		newEntity.newHealth = _entity.health();
		newEntity.newExtendedData = _entity.extendedData();
	}

	/**
	 * Called to serialize the update into the given buffer for network transmission.
	 * 
	 * @param buffer The network buffer where the update should be written.
	 */
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writePartialEntity(buffer, _entity);
	}
}
