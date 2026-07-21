package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.MutableSlotManager;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Asks the entity to move items from its inventory into a block.  Calls MutationBlockStoreItems to update the block
 * inventory.
 * Note that races here can result in items being destroyed (if the same block is over-filled in one tick).
 */
public class EntitySubActionPushItems implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.ITEMS_REQUEST_PUSH;

	public static EntitySubActionPushItems deserializeFromContext(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		int localInventoryId = buffer.getInt();
		Assert.assertTrue(localInventoryId > 0);
		int count = buffer.getInt();
		Assert.assertTrue(count > 0);
		byte inventoryAspect = buffer.get();
		Assert.assertTrue((Inventory.INVENTORY_ASPECT_INVENTORY == inventoryAspect) || (Inventory.INVENTORY_ASPECT_FUEL == inventoryAspect));
		return new EntitySubActionPushItems(blockLocation, localInventoryId, count, inventoryAspect);
	}


	private final AbsoluteLocation _blockLocation;
	private final int _localInventoryId;
	private final int _count;
	private final byte _inventoryAspect;

	public EntitySubActionPushItems(AbsoluteLocation blockLocation, int localInventoryId, int count, byte inventoryAspect)
	{
		Assert.assertTrue(localInventoryId > 0);
		Assert.assertTrue(count > 0);
		
		_blockLocation = blockLocation;
		_localInventoryId = localInventoryId;
		_count = count;
		_inventoryAspect = inventoryAspect;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		boolean didApply = false;
		
		// Make sure that we actually have this much of the referenced item in our inventory.
		MutableSlotManager slotManager = newEntity.getSlotManager();
		ItemSlot slot = slotManager.getSlot(_localInventoryId);
		boolean isValidKey = (null != slot);
		
		// We want to make sure that this is a block which can accept items.
		boolean canTransfer;
		Inventory inv;
		if (isValidKey)
		{
			BlockProxy block = context.previousBlockLookUp.readBlock(_blockLocation);
			Item type = slot.getType();
			if (null != slot.stack)
			{
				canTransfer = (slot.stack.count() >= _count);
				inv = _getInventory(block, type);
			}
			else
			{
				canTransfer = true;
				inv = _getInventory(block, type);
				// In this case, it MUST be only 1.
				if (1 != _count)
				{
					isValidKey = false;
				}
			}
		}
		else
		{
			canTransfer = false;
			inv = null;
		}
		
		// We also want to make sure that this is in range.
		EntityLocation sourceEyeLocation = SpatialHelpers.getEntityEye(newEntity);
		float distance = SpatialHelpers.distanceFromLocationToBlockSurface(sourceEyeLocation, _blockLocation);
		boolean isInRange = (distance <= MiscConstants.REACH_BLOCK);
		
		if (isValidKey && canTransfer && isInRange && (null != inv))
		{
			// See if there is space in the inventory.
			MutableInventory checker = new MutableInventory(inv);
			
			Item type = slot.getType();
			int capacity = checker.maxVacancyForItem(type);
			int toDrop = Math.min(capacity, _count);
			if (toDrop > 0)
			{
				// We will proceed to remove the items from our inventory and pass them to the block.
				Items stackToMove;
				if (null != slot.stack)
				{
					slotManager.removeStackable(type, toDrop);
					stackToMove = new Items(type, toDrop);
				}
				else
				{
					slotManager.removeNonStackable(_localInventoryId);
					stackToMove = null;
				}
				context.mutationSink.next(new MutationBlockStoreItems(_blockLocation, stackToMove, slot.nonStackable, _inventoryAspect));
				
				newEntity.setCurrentChargeMillis(0);
				didApply = true;
			}
		}
		return didApply;
	}

	@Override
	public EntitySubActionType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		buffer.putInt(_localInventoryId);
		buffer.putInt(_count);
		buffer.put(_inventoryAspect);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This has a block reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Push " + _count + " items of local inventory key " + _localInventoryId + " to " + _blockLocation + " (inventory aspect " + _inventoryAspect + ")";
	}


	private Inventory _getInventory(BlockProxy block, Item type)
	{
		Environment env = Environment.getShared();
		Inventory inv;
		switch (_inventoryAspect)
		{
		case Inventory.INVENTORY_ASPECT_INVENTORY:
			inv = block.getInventory();
			break;
		case Inventory.INVENTORY_ASPECT_FUEL:
			inv = env.fuel.hasFuelInventoryForType(block.getBlock(), type)
				? block.getFuel().fuelInventory()
				: null
			;
			break;
		default:
			throw Assert.unreachable();
		}
		return inv;
	}
}
