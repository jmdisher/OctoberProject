package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
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
		newEntity.newInventory.clearInventory(_entity.inventory());
		newEntity.newLocation = _entity.location();
		newEntity.newHotbar = _entity.hotbarItems();
		newEntity.newHotbarIndex = _entity.hotbarIndex();
		newEntity.newArmour = _entity.armourSlots();
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
