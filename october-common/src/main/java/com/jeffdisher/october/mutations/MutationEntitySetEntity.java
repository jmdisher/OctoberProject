package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;


/**
 * Updates the entity by setting its whole state.
 */
public class MutationEntitySetEntity implements IEntityUpdate
{
	public static final EntityUpdateType TYPE = EntityUpdateType.WHOLE_ENTITY;

	public static MutationEntitySetEntity deserializeFromNetworkBuffer(ByteBuffer buffer)
	{
		// This is always coming in from the network so it has no version-specific considerations.
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
		);
		Entity entity = CodecHelpers.readEntity(context);
		return new MutationEntitySetEntity(entity);
	}


	private final Entity _entity;

	public MutationEntitySetEntity(Entity entity)
	{
		_entity = entity;
	}

	@Override
	public void applyToEntity(MutableEntity newEntity)
	{
		newEntity.newInventory.clearInventory(_entity.inventory());
		newEntity.newLocation = _entity.location();
		newEntity.newHotbar = _entity.hotbarItems();
		newEntity.newHotbarIndex = _entity.hotbarIndex();
		newEntity.newArmour = _entity.armourSlots();
		newEntity.newVelocity = _entity.velocity();
		newEntity.newYaw = _entity.yaw();
		newEntity.newPitch = _entity.pitch();
		newEntity.newLocalCraftOperation = _entity.localCraftOperation();
		newEntity.newHealth = _entity.health();
		newEntity.newFood = _entity.food();
		newEntity.newBreath = _entity.breath();
		newEntity.isCreativeMode = _entity.isCreativeMode();
		newEntity.newSpawn = _entity.spawnLocation();
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
