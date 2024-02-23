package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Only sent server->client in order to immediately set an entity to an authoritative state.
 * TODO:  In the future, this needs to be parameterized or carved up to only do partial updates.
 */
public class MutationEntitySetEntity implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.SET_ENTITY;

	public static MutationEntitySetEntity deserializeFromBuffer(ByteBuffer buffer)
	{
		Entity entity = CodecHelpers.readEntity(buffer);
		return new MutationEntitySetEntity(entity);
	}


	private final Entity _entity;

	public MutationEntitySetEntity(Entity entity)
	{
		_entity = entity;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		newEntity.newInventory.clearInventory();
		for (Items items : _entity.inventory().items.values())
		{
			newEntity.newInventory.addAllItems(items.type(), items.count());
		}
		newEntity.newLocation = _entity.location();
		newEntity.newSelectedItem = _entity.selectedItem();
		newEntity.newZVelocityPerSecond = _entity.zVelocityPerSecond();
		newEntity.newLocalCraftOperation = _entity.localCraftOperation();
		return true;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntity(buffer, _entity);
	}
}
