package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
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
