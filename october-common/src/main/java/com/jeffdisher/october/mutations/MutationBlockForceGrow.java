package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Forces a growth operation onto the target block.
 */
public class MutationBlockForceGrow implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.FORCE_GROW;
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;
	public static final byte MIN_LIGHT = 5;

	public static MutationBlockForceGrow deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockForceGrow(location);
	}


	private final AbsoluteLocation _location;

	public MutationBlockForceGrow(AbsoluteLocation location)
	{
		_location = location;
	}

	@Override
	public AbsoluteLocation getAbsoluteLocation()
	{
		return _location;
	}

	@Override
	public boolean applyMutation(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		boolean didApply = false;
		// Make sure that this is a block which can grow.
		Block block = newBlock.getBlock();
		if (PlantHelpers.canGrow(env, block))
		{
			PlantHelpers.performForcedGrow(env, context, _location, newBlock, block);
			
			// Note that the force grow doesn't reschedule the growth mutation since that is independent.
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
