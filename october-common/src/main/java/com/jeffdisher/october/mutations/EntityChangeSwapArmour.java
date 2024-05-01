package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Swaps the given armour slot with the given inventory key.
 * Note that swapping the armour into the inventory will create a new key and only armour non-stackable items can be
 * swapped from the inventory.
 */
public class EntityChangeSwapArmour implements IMutationEntity
{
	public static final MutationEntityType TYPE = MutationEntityType.SWAP_ARMOUR;

	public static EntityChangeSwapArmour deserializeFromBuffer(ByteBuffer buffer)
	{
		BodyPart slot = CodecHelpers.readBodyPart(buffer);
		int inventoryId = buffer.getInt();
		return new EntityChangeSwapArmour(slot, inventoryId);
	}


	private final BodyPart _slot;
	private final int _inventoryId;

	public EntityChangeSwapArmour(BodyPart slot, int inventoryId)
	{
		Assert.assertTrue(null != slot);
		
		_slot = slot;
		_inventoryId = inventoryId;
	}

	@Override
	public long getTimeCostMillis()
	{
		return 0L;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, MutableEntity newEntity)
	{
		boolean didApply = false;
		Environment env = Environment.getShared();
		
		// Make sure that the inventory key references a valid type and that there is something to swap.
		NonStackableItem fromInventory = newEntity.newInventory.getNonStackableForKey(_inventoryId);
		BodyPart inventoryArmour = (null != fromInventory) ? env.armour.getBodyPart(fromInventory.type()) : _slot;
		NonStackableItem fromArmour = newEntity.newArmour[_slot.ordinal()];
		if ((_slot == inventoryArmour) && ((null != fromInventory) || (null != fromArmour)))
		{
			// Both are talking about the same slot and there is at least something to move so swap.
			newEntity.newArmour[_slot.ordinal()] = fromInventory;
			if (null != fromInventory)
			{
				newEntity.newInventory.removeNonStackableItems(_inventoryId);
				newEntity.clearHotBarWithKey(_inventoryId);
			}
			if (null != fromArmour)
			{
				// In this case, we will allow it to become over-filled.
				newEntity.newInventory.addNonStackableAllowingOverflow(fromArmour);
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
		CodecHelpers.writeBodyPart(buffer, _slot);
		buffer.putInt(_inventoryId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
