package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.DeserializationContext;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.FireHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are created when another block starts burning, when lava flows into a block, or when a block is
 * placed next to fire or lava.
 */
public class MutationBlockStartFire implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.START_FIRE;
	/**
	 * We will delay ignition by 2 seconds.
	 */
	public static final long IGNITION_DELAY_MILLIS = 2_000L;

	public static MutationBlockStartFire deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockStartFire(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockStartFire(AbsoluteLocation blockLocation)
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
		
		// Check if this is flammable and isn't already burning.
		Environment env = Environment.getShared();
		if (FireHelpers.canIgnite(env, context, _blockLocation, newBlock))
		{
			CommonBlockMutationHelpers.igniteBlockAndSpread(env, context, _blockLocation, newBlock);
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
