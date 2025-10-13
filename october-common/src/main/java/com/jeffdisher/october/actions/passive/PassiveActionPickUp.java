package com.jeffdisher.october.actions.passive;

import com.jeffdisher.october.actions.MutationEntityStoreToInventory;
import com.jeffdisher.october.types.IPassiveAction;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PassiveEntity;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Sent by a player entity in order to pick up the stack.
 * Note that the PassiveEntity design assumes that the extended data doesn't change (as it can be complicated/large) so
 * the requesting player entity must receive ALL of it (potentially dropping another passive on overflow).
 * Note, also, that this does no checking on distance, etc, assuming that such checks were already implemented by the
 * caller.
 */
public class PassiveActionPickUp implements IPassiveAction
{
	private final int _callingEntityId;

	public PassiveActionPickUp(int callingEntityId)
	{
		// This MUST be a player entity (at least for now).
		Assert.assertTrue(callingEntityId > 0);
		_callingEntityId = callingEntityId;
	}

	@Override
	public PassiveEntity applyChange(TickProcessingContext context, PassiveEntity entity)
	{
		// Currently, we only have the ItemSlot type.
		PassiveType type = entity.type();
		Assert.assertTrue(PassiveType.ITEM_SLOT == type);
		
		ItemSlot slot = (ItemSlot) entity.extendedData();
		MutationEntityStoreToInventory store = new MutationEntityStoreToInventory(slot.stack, slot.nonStackable);
		
		boolean didSchedule = context.newChangeSink.next(_callingEntityId, store);
		
		// We will despawn if we sent the change.
		return didSchedule
			? null
			: entity
		;
	}
}
