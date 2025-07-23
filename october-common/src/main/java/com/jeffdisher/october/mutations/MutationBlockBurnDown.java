package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are created when MutationBlockStartFire starts a fire.  The point is to destroy the block if it is
 * still on fire when this runs (the block wasn't extinguished or replaced).
 */
public class MutationBlockBurnDown implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.BURN_DOWN;
	/**
	 * We will let things burn for 10 seconds.
	 */
	public static final long BURN_DELAY_MILLIS = 10_000L;

	public static MutationBlockBurnDown deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockBurnDown(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockBurnDown(AbsoluteLocation blockLocation)
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
		boolean didApply = false;
		
		// Check that this is currently on fire.
		byte flags = newBlock.getFlags();
		if (FlagsAspect.isSet(flags, FlagsAspect.FLAG_BURNING))
		{
			// Now, destroy the block.
			Environment env = Environment.getShared();
			
			// When a block burns, destroy it and any inventory.
			Block emptyBlock = env.special.AIR;
			CommonBlockMutationHelpers.setBlockCheckingFire(env, context, _blockLocation, newBlock, emptyBlock);
			
			// We want to see if there are any liquids around this block which we will need to handle.
			Block eventualBlock = CommonBlockMutationHelpers.determineEmptyBlockType(context, _blockLocation, emptyBlock);
			if (emptyBlock != eventualBlock)
			{
				long millisDelay = env.liquids.minFlowDelayMillis(env, eventualBlock, eventualBlock);
				context.mutationSink.future(new MutationBlockLiquidFlowInto(_blockLocation), millisDelay);
			}
			
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
