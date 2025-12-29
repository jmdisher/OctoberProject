package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Drops items in the receiver's inventory as a passive ItemSlot.
 */
public class EntitySubActionDropItemsAsPassive implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.DROP_ITEMS_AS_PASSIVE;

	public static EntitySubActionDropItemsAsPassive deserializeFromBuffer(ByteBuffer buffer)
	{
		int localInventoryId = buffer.getInt();
		Assert.assertTrue(localInventoryId > 0);
		boolean dropAll = CodecHelpers.readBoolean(buffer);
		return new EntitySubActionDropItemsAsPassive(localInventoryId, dropAll);
	}


	private final int _localInventoryId;
	private final boolean _dropAll;

	public EntitySubActionDropItemsAsPassive(int localInventoryId, boolean dropAll)
	{
		Assert.assertTrue(localInventoryId > 0);
		
		_localInventoryId = localInventoryId;
		_dropAll = dropAll;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Make sure that we actually have this much of the referenced item in our inventory.
		IMutableInventory mutableInventory = newEntity.accessMutableInventory();
		ItemSlot slot = mutableInventory.getSlotForKey(_localInventoryId);
		
		if (null != slot)
		{
			ItemSlot slotToMove;
			if (null != slot.stack)
			{
				Items stackToDrop;
				if (!_dropAll && (slot.stack.count() > 1))
				{
					// We will just drop 1.
					stackToDrop = new Items(slot.stack.type(), 1);
				}
				else
				{
					// Drop the whole stack.
					stackToDrop = slot.stack;
				}
				mutableInventory.removeStackableItems(stackToDrop.type(), stackToDrop.count());
				slotToMove = ItemSlot.fromStack(stackToDrop);
			}
			else
			{
				mutableInventory.removeNonStackableItems(_localInventoryId);
				slotToMove = ItemSlot.fromNonStack(slot.nonStackable);
			}
			
			// Drop the passive.
			EntityLocation velocity = new EntityLocation(0.0f, 0.0f, 0.0f);
			context.passiveSpawner.spawnPassive(PassiveType.ITEM_SLOT, newEntity.getLocation(), velocity, slotToMove);
			
			// If this removed something from the inventory, entirely, make sure it is removed from any hotbar slots.
			boolean shouldClear = (null != slot.nonStackable) || (0 == mutableInventory.getCount(slot.stack.type()));
			if (shouldClear)
			{
				newEntity.clearHotBarWithKey(_localInventoryId);
			}
		}
		else
		{
			// This isn't in the inventory (sometimes happens when the action is taken when out of sync).
		}
		
		// We will succeed if there was something to drop.
		return (null != slot);
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		buffer.putInt(_localInventoryId);
		CodecHelpers.writeBoolean(buffer, _dropAll);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Default case.
		return true;
	}

	@Override
	public String toString()
	{
		return "Drop " + (_dropAll ? "all items" : "1 item") + "  of local inventory key " + _localInventoryId;
	}
}
