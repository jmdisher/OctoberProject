package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Applies damage to an entity, potentially reseting them to world spawn and dropping their inventory.
 * Note that the damage origin is passed in here and checked for range but that will likely be moved to the sender, in
 * the future.
 */
public class EntityChangeTakeDamage implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.TAKE_DAMAGE;

	public static EntityChangeTakeDamage deserializeFromBuffer(ByteBuffer buffer)
	{
		EntityLocation source = CodecHelpers.readEntityLocation(buffer);
		byte damage = buffer.get();
		return new EntityChangeTakeDamage(source, damage);
	}


	private final EntityLocation _source;
	private final byte _damage;

	public EntityChangeTakeDamage(EntityLocation source, byte damage)
	{
		// Assume that there is always a source.
		Assert.assertTrue(null != source);
		// Make sure that this is positive.
		Assert.assertTrue(damage > 0);
		
		_source = source;
		_damage = damage;
	}

	@Override
	public long getTimeCostMillis()
	{
		// Taking damage doesn't use any time.
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		// Check that we are in range of the source (will likely move to sender, later).  We will use block breaking distance.
		EntityLocation entityCentre = SpatialHelpers.getEntityCentre(newEntity.newLocation, newEntity.original.volume());
		float absX = Math.abs(_source.x() - entityCentre.x());
		float absY = Math.abs(_source.y() - entityCentre.y());
		float absZ = Math.abs(_source.z() - entityCentre.z());
		boolean isLocationClose = ((absX <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absY <= EntityChangeIncrementalBlockBreak.MAX_REACH) && (absZ <= EntityChangeIncrementalBlockBreak.MAX_REACH));
		
		// We will move the respawn into the next tick so that they don't keep taking damage from within this tick.
		boolean didApply = false;
		if (isLocationClose && (newEntity.newHealth > 0))
		{
			byte finalHealth = (byte)(newEntity.newHealth - _damage);
			if (finalHealth > 0)
			{
				// We can apply the damage.
				newEntity.newHealth = finalHealth;
			}
			else
			{
				// The entity is dead so "respawn" them by resetting fields and dropping inventory onto the ground.
				newEntity.newLocation = MutableEntity.DEFAULT_LOCATION;
				newEntity.newHealth = MutableEntity.DEFAULT_HEALTH;
				for (Items items : newEntity.newInventory.freeze().items.values())
				{
					context.newMutationSink.accept(new MutationBlockStoreItems(entityCentre.getBlockLocation(), items, Inventory.INVENTORY_ASPECT_INVENTORY));
				}
				newEntity.newInventory.clearInventory();
				newEntity.newSelectedItem = null;
			}
			didApply = true;
		}
		return didApply;
	}

	@Override
	public MutationEntityType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeEntityLocation(buffer, _source);
		buffer.put(_damage);
	}
}
