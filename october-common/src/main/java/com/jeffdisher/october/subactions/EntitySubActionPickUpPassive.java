package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.passive.PassiveActionPickUp;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Verifies that we can pick up at least some of what is in the given passive, then we issue it a PassiveActionPickUp.
 * We don't enforce any kind of cool-down on these calls but it probably makes sense for the client to provide some kind
 * of rate limiting so it doesn't immediately pick up what was dropped, for example.
 */
public class EntitySubActionPickUpPassive implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.PICK_UP_ITEMS_PASSIVE;
	public static final float PICKUP_DISTANCE = 2.0f;

	public static EntitySubActionPickUpPassive deserializeFromBuffer(ByteBuffer buffer)
	{
		int passiveId = buffer.getInt();
		return new EntitySubActionPickUpPassive(passiveId);
	}


	private final int _passiveId;

	public EntitySubActionPickUpPassive(int passiveId)
	{
		Assert.assertTrue(passiveId > 0);
		
		_passiveId = passiveId;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		// Verify that this passive exists, is within range, and we have space for at least something from it.
		boolean didApply = false;
		PartialPassive passive = context.previousPassiveLookUp.apply(_passiveId);
		if ((null != passive)
			&& (PassiveType.ITEM_SLOT == passive.type())
			&& (SpatialHelpers.distanceFromPlayerEyeToVolume(newEntity.getLocation(), newEntity.getType(), passive.location(), passive.type().volume()) <= PICKUP_DISTANCE)
		)
		{
			ItemSlot contents = (ItemSlot) passive.extendedData();
			Item type = (null != contents.stack)
				? contents.stack.type()
				: contents.nonStackable.type()
			;
			if (newEntity.accessMutableInventory().maxVacancyForItem(type) > 0)
			{
				// We can pick up at least one of these so send on the request.
				PassiveActionPickUp pickup = new PassiveActionPickUp(newEntity.getId());
				context.newChangeSink.passive(_passiveId, pickup);
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
		buffer.putInt(_passiveId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Contains a passive reference.
		return false;
	}

	@Override
	public String toString()
	{
		return "Pick up passive " + _passiveId;
	}
}
