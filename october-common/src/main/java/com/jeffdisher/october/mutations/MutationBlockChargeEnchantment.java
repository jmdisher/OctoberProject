package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.EnchantingBlockSupport;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Part of the enchantment charging flow:  Called by EnchantingBlockSupport when a new enchantment starts or in response
 * to one of these instances running on an existing enchantment which still needs more charging.
 */
public class MutationBlockChargeEnchantment implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CHARGE_ENCHANTMENT;

	public static MutationBlockChargeEnchantment deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		long previousChargeLevel = buffer.getLong();
		return new MutationBlockChargeEnchantment(location, previousChargeLevel);
	}


	private final AbsoluteLocation _blockLocation;
	private final long _previousChargeLevel;

	public MutationBlockChargeEnchantment(AbsoluteLocation blockLocation, long previousChargeLevel)
	{
		_blockLocation = blockLocation;
		_previousChargeLevel = previousChargeLevel;
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
			EnchantingBlockSupport.chargeEnchantingOperation(env, context, _blockLocation, newBlock, _previousChargeLevel);
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
		buffer.putLong(_previousChargeLevel);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
