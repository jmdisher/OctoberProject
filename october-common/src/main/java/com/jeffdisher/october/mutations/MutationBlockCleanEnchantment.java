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
 * The final step in the enchantment completion flow, enqueued by EnchantingBlockSupport to run 2 ticks after
 * MutationBlockFetchSpecialForEnchantment runs to fetch slots from pedestals and 1 tick after
 * MutationBlockReceiveSpecialForEnchantment run to store them into this block.
 * This mutation will abort and discard the enchantment if it is fully charged and still waiting for required items, as
 * that means it has failed (those would have arrived in the previous tick).
 */
public class MutationBlockCleanEnchantment implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CLEAN_ENCHANTMENT;

	public static MutationBlockCleanEnchantment deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockCleanEnchantment(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockCleanEnchantment(AbsoluteLocation blockLocation)
	{
		_blockLocation = blockLocation;
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
			EnchantingBlockSupport.cleanUpOrphanedOperations(env, context, _blockLocation, newBlock);
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
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
