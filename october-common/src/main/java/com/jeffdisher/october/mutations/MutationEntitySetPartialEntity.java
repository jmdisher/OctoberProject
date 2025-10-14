package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutablePartialEntity;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Updates the partial entity by setting its whole state.
 */
public class MutationEntitySetPartialEntity implements IPartialEntityUpdate
{
	public static final PartialEntityUpdateType TYPE = PartialEntityUpdateType.WHOLE_PARTIAL_ENTITY;

	public static MutationEntitySetPartialEntity deserializeFromNetworkBuffer(ByteBuffer buffer)
	{
		PartialEntity entity = CodecHelpers.readPartialEntity(buffer);
		return new MutationEntitySetPartialEntity(entity);
	}

	/**
	 * Checks if this change encoding would even show anything.  This is used in cases where the change to an entity
	 * might be in data not relevant for PartialEntity instances, meaning it can be skipped.
	 * 
	 * @param previousEntityVersion The version of the entity from the previous tick.
	 * @param currentEntityVersion The current version of the entity from this tick.
	 * @return
	 */
	public static boolean canDescribeChange(Entity previousEntityVersion, Entity currentEntityVersion)
	{
		return (!previousEntityVersion.location().equals(currentEntityVersion.location()))
			|| (previousEntityVersion.yaw() != currentEntityVersion.yaw())
			|| (previousEntityVersion.pitch() != currentEntityVersion.pitch())
			|| (previousEntityVersion.health() != currentEntityVersion.health())
		;
	}


	private final PartialEntity _entity;

	public MutationEntitySetPartialEntity(PartialEntity entity)
	{
		_entity = entity;
	}

	@Override
	public void applyToEntity(MutablePartialEntity newEntity)
	{
		newEntity.newLocation = _entity.location();
		newEntity.newYaw = _entity.yaw();
		newEntity.newPitch = _entity.pitch();
		newEntity.newHealth = _entity.health();
	}

	@Override
	public PartialEntityUpdateType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writePartialEntity(buffer, _entity);
	}
}
