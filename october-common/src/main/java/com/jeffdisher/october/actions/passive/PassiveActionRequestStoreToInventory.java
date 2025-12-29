package com.jeffdisher.october.actions.passive;

import com.jeffdisher.october.mutations.MutationBlockStoreItems;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Sent by a hopper in order to absorb a passive ITEM_SLOT.
 * This causes the passive to despawn and send its contents to the hopper's inventory (where it might re-spawn as a
 * passive if the block has changed).
 */
public class PassiveActionRequestStoreToInventory implements IPassiveAction
{
	private final AbsoluteLocation _targetBlockLocation;

	public PassiveActionRequestStoreToInventory(AbsoluteLocation targetBlockLocation)
	{
		_targetBlockLocation = targetBlockLocation;
	}

	@Override
	public PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
	{
		// This can only be applied to ITEM_SLOT types.
		PassiveType type = entity.type();
		Assert.assertTrue(PassiveType.ITEM_SLOT == type);
		
		ItemSlot slot = (ItemSlot) entity.extendedData();
		MutationBlockStoreItems storeToSink = new MutationBlockStoreItems(_targetBlockLocation, slot.stack, slot.nonStackable, Inventory.INVENTORY_ASPECT_INVENTORY);
		boolean didSchedule = context.mutationSink.next(storeToSink);
		
		// We will despawn if we sent the change.
		return didSchedule
			? null
			: entity
		;
	}
}
