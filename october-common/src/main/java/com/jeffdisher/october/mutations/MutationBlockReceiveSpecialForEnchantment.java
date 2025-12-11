package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.EnchantingBlockSupport;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Part of the enchantment completion flow, called by MutationBlockFetchSpecialForEnchantment to pass back the requested
 * item slot.
 */
public class MutationBlockReceiveSpecialForEnchantment implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.RECEIVE_SPECIAL_FOR_ENCHANTMENT;

	public static MutationBlockReceiveSpecialForEnchantment deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		ItemSlot special = CodecHelpers.readSlot(context);
		return new MutationBlockReceiveSpecialForEnchantment(location, special);
	}


	private final AbsoluteLocation _blockLocation;
	private final ItemSlot _special;

	public MutationBlockReceiveSpecialForEnchantment(AbsoluteLocation blockLocation, ItemSlot special)
	{
		_blockLocation = blockLocation;
		_special = special;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _blockLocation;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		
		// Just call the state machine helper.
		if (env.enchantments.canEnchant(newBlock.getBlock()))
		{
			EnchantingBlockSupport.receiveConsumedInputItem(env, context, _blockLocation, newBlock, _special);
			// We just assume that this always applies.
			didApply = true;
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
		CodecHelpers.writeSlot(buffer, _special);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
