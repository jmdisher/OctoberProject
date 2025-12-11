package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Used specifically for the enchantment completion flow:  Created by EnchantingBlockSupport once an enchantment is done
 * charging to fetch the input item from this block's special slot.
 */
public class MutationBlockFetchSpecialForEnchantment implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.FETCH_SPECIAL_FOR_ENCHANTMENT;

	public static MutationBlockFetchSpecialForEnchantment deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		AbsoluteLocation returnLocation = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockFetchSpecialForEnchantment(location, returnLocation);
	}


	private final AbsoluteLocation _blockLocation;
	private final AbsoluteLocation _returnLocation;

	public MutationBlockFetchSpecialForEnchantment(AbsoluteLocation blockLocation, AbsoluteLocation returnLocation)
	{
		_blockLocation = blockLocation;
		_returnLocation = returnLocation;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		// See if we have a special slot.
		ItemSlot slot = newBlock.getSpecialSlot();
		if (null != slot)
		{
			// Carve off a single item to send back to _returnLocation and store the remainder (or clear).
			ItemSlot toSend;
			ItemSlot toRestore;
			if (slot.getCount() > 1)
			{
				// Carve off one (we know that this must be stackable).
				toSend = ItemSlot.fromStack(new Items(slot.getType(), 1));
				toRestore = ItemSlot.fromStack(new Items(slot.getType(), slot.getCount() - 1));
			}
			else
			{
				// Just take the whole thing.
				toSend = slot;
				toRestore = null;
			}
			
			MutationBlockReceiveSpecialForEnchantment send = new MutationBlockReceiveSpecialForEnchantment(_returnLocation, toSend);
			context.mutationSink.next(send);
			newBlock.setSpecialSlot(toRestore);
		}
		
		// Return true if there was a special.
		return (null != slot);
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
		CodecHelpers.writeAbsoluteLocation(buffer, _returnLocation);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
