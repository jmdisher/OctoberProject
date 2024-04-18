package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Updates the entity by setting its whole state.
 */
public class MutationEntitySetEntity implements IEntityUpdate
{
	public static final EntityUpdateType TYPE = EntityUpdateType.WHOLE_ENTITY;

	public static MutationEntitySetEntity deserializeFromNetworkBuffer(ByteBuffer buffer)
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
	public void applyToEntity(TickProcessingContext context, MutableEntity newEntity)
	{
		newEntity.newInventory.clearInventory();
		for (Items items : _entity.inventory().sortedItems())
		{
			newEntity.newInventory.addAllItems(items.type(), items.count());
		}
		newEntity.newLocation = _entity.location();
		newEntity.newSelectedItem = _entity.selectedItem();
		newEntity.newZVelocityPerSecond = _entity.zVelocityPerSecond();
		newEntity.newLocalCraftOperation = _entity.localCraftOperation();
		newEntity.newHealth = _entity.health();
		newEntity.newFood = _entity.food();
	}

	@Override
	public EntityUpdateType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntity(buffer, _entity);
	}
}
