package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.TickProcessingContext;


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
	public void applyToEntity(TickProcessingContext context, MutableEntity newEntity)
	{
		newEntity.newLocation = _entity.location();
		newEntity.newZVelocityPerSecond = _entity.zVelocityPerSecond();
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
