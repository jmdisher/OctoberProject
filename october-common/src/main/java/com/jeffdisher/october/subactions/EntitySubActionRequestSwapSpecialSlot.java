package com.jeffdisher.october.subactions;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntitySubActionType;
import com.jeffdisher.october.mutations.MutationBlockSwapSpecialSlot;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutableInventory;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * This sub-action is similar to MutationEntityRequestItemPickUp but specifically for interacting with the special item
 * slot on some kinds of blocks.
 * This is a multi-step process since both the entity and the block need to change and the request needs to start from
 * an entity:
 * -EntitySubActionRequestSwapSpecialSlot - checks that the target and selected stack make sense
 * -MutationBlockSwapSpecialSlot - run against the block to swap the item stack
 * -MutationEntityStoreToInventory - run against the entity to store the swapped out items (if there were any)
 */
public class EntitySubActionRequestSwapSpecialSlot implements IEntitySubAction<IMutablePlayerEntity>
{
	public static final EntitySubActionType TYPE = EntitySubActionType.ITEM_SLOT_REQUEST_SWAP;

	public static EntitySubActionRequestSwapSpecialSlot deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation blockLocation = CodecHelpers.readAbsoluteLocation(buffer);
		boolean sendAll = CodecHelpers.readBoolean(buffer);
		return new EntitySubActionRequestSwapSpecialSlot(blockLocation, sendAll);
	}


	private final AbsoluteLocation _blockLocation;
	private final boolean _sendAll;

	public EntitySubActionRequestSwapSpecialSlot(AbsoluteLocation blockLocation, boolean sendAll)
	{
		_blockLocation = blockLocation;
		_sendAll = sendAll;
	}

	@Override
	public boolean applyChange(TickProcessingContext context, IMutablePlayerEntity newEntity)
	{
		Environment env = Environment.getShared();
		
		float distance = SpatialHelpers.distanceFromMutableEyeToBlockSurface(newEntity, _blockLocation);
		boolean isInRange = (distance <= MiscConstants.REACH_BLOCK);
		
		// Check that the target block has a special slot.
		BlockProxy proxy = context.previousBlockLookUp.readBlock(_blockLocation);
		boolean didApply = false;
		if (isInRange && (null != proxy) && env.specialSlot.hasSpecialSlot(proxy.getBlock()))
		{
			// See if we should be sending anything with this request, or just pulling.
			IMutableInventory inv = newEntity.accessMutableInventory();
			int key = newEntity.getSelectedKey();
			ItemSlot selectedSlot = inv.getSlotForKey(key);
			
			// We want to send just one item, if this is a stack.  The other side will absorb it if it already has the
			// same item type, or swap out if otherwise.
			ItemSlot toPush;
			if (!_sendAll && (null != selectedSlot) && (null != selectedSlot.stack))
			{
				Item type = selectedSlot.stack.type();
				toPush = ItemSlot.fromStack(new Items(type, 1));
			}
			else
			{
				toPush = selectedSlot;
			}
			
			if (null != toPush)
			{
				if (null != toPush.stack)
				{
					inv.removeStackableItems(toPush.stack.type(), toPush.stack.count());
				}
				else
				{
					inv.removeNonStackableItems(key);
				}
				boolean shouldClear = (null != toPush.nonStackable) || (0 == inv.getCount(toPush.stack.type()));
				if (shouldClear)
				{
					newEntity.clearHotBarWithKey(key);
				}
			}
			
			// Send the block mutation to swap (toPush could be null, but that is ok).
			MutationBlockSwapSpecialSlot mutation = new MutationBlockSwapSpecialSlot(_blockLocation, toPush, newEntity.getId());
			context.mutationSink.next(mutation);
			newEntity.setCurrentChargeMillis(0);
			didApply = true;
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
		CodecHelpers.writeBoolean(buffer, _sendAll);
	}

	@Override
	public boolean canSaveToDisk()
	{
		return true;
	}

	@Override
	public String toString()
	{
		return "Request swap at " + _blockLocation + ", all: " + _sendAll + ")";
	}
}
