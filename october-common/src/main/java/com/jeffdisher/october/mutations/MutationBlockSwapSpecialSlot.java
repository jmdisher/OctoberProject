package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.actions.EntityActionStoreToInventory;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called by EntitySubActionRequestSwapSpecialSlot to request that items be stored and/or extracted from the special
 * slot and sent back to the calling entity.
 */
public class MutationBlockSwapSpecialSlot implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.SWAP_SPECIAL_SLOT;

	public static MutationBlockSwapSpecialSlot deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		ItemSlot slot = CodecHelpers.readSlot(context);
		int returnEntity = buffer.getInt();
		return new MutationBlockSwapSpecialSlot(location, slot, returnEntity);
	}


	private final AbsoluteLocation _blockLocation;
	private final ItemSlot _slot;
	private final int _returnEntityId;

	public MutationBlockSwapSpecialSlot(AbsoluteLocation blockLocation, ItemSlot slot, int returnEntityId)
	{
		_blockLocation = blockLocation;
		_slot = slot;
		_returnEntityId = returnEntityId;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		boolean didApply = false;
		
		Environment env = Environment.getShared();
		Block block = newBlock.getBlock();
		if (env.specialSlot.hasSpecialSlot(block))
		{
			// This has a special slot so see if we can swap it or if it is compatible.
			ItemSlot existing = newBlock.getSpecialSlot();
			boolean canMerge = ((null != existing) && (null != _slot) && (null != existing.stack) && (existing.getType() == _slot.getType()));
			if (canMerge)
			{
				// We can stack these together without sending anything back.
				Items combined = new Items(existing.getType(), existing.stack.count() + _slot.stack.count());
				newBlock.setSpecialSlot(ItemSlot.fromStack(combined));
			}
			else
			{
				// See if we will send something back.
				boolean canRemoveOrDrop = env.specialSlot.canRemoveOrDrop(block);
				if (canRemoveOrDrop || (null == existing))
				{
					newBlock.setSpecialSlot(_slot);
				}
				if ((null != existing) && (_returnEntityId > 0) && canRemoveOrDrop)
				{
					EntityActionStoreToInventory storeAction = new EntityActionStoreToInventory(existing.stack, existing.nonStackable);
					context.newChangeSink.next(_returnEntityId, storeAction);
				}
			}
			
			didApply = true;
		}
		
		// If we failed to apply this, send the items back.
		if (!didApply && (_returnEntityId > 0))
		{
			EntityActionStoreToInventory storeAction = new EntityActionStoreToInventory(_slot.stack, _slot.nonStackable);
			context.newChangeSink.next(_returnEntityId, storeAction);
		}
		return didApply;
	}

	@Override
	public MutationBlockType getType()
	{
		return TYPE;
	}

	@Override
	public void serializeToBuffer(ByteBuffer buffer)
	{
		CodecHelpers.writeAbsoluteLocation(buffer, _blockLocation);
		CodecHelpers.writeSlot(buffer, _slot);
		buffer.putInt(_returnEntityId);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// This depends on a return entity ID.
		return false;
	}
}
