package com.jeffdisher.october.net;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.MutableEntity;


/**
 * Updates the entity by setting its whole state.
 */
public class MutationEntitySetEntity
{
	public static MutationEntitySetEntity deserializeFromNetworkBuffer(ByteBuffer buffer)
	{
		// This is always coming in from the network so it has no version-specific considerations.
		DeserializationContext context = new DeserializationContext(Environment.getShared()
			, buffer
			, 0L
			, false
		);
		Entity entity = CodecHelpers.readEntityNetwork(context);
		return new MutationEntitySetEntity(entity);
	}


	private final Entity _entity;

	public MutationEntitySetEntity(Entity entity)
	{
		_entity = entity;
	}

	/**
	 * Applies the receiver to the given newEntity.
	 * 
	 * @param newEntity The entity which should be updated by the receiver.
	 */
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
		newEntity.newLocalCraftOperation = _entity.ephemeralShared().localCraftOperation();
		newEntity.chargeMillis = _entity.ephemeralShared().chargeMillis();
		newEntity.newHealth = _entity.health();
		newEntity.newFood = _entity.food();
		newEntity.newBreath = _entity.breath();
		newEntity.isCreativeMode = _entity.isCreativeMode();
		newEntity.newSpawn = _entity.spawnLocation();
	}

	/**
	 * Called to serialize the update into the given buffer for network transmission.
	 * 
	 * @param buffer The network buffer where the update should be written.
	 */
	public void serializeToNetworkBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityNetwork(buffer, _entity);
	}
}
