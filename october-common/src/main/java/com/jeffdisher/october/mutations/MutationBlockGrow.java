package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Attempts to apply "growth" to the given block, potentially replacing it with a mature plant or scheduling a future
 * growth operation.
 */
public class MutationBlockGrow implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.GROW;
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;

	public static MutationBlockGrow deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		boolean forceGrow = CodecHelpers.readBoolean(buffer);
		return new MutationBlockGrow(location, forceGrow);
	}


	private final AbsoluteLocation _location;
	private final boolean _forceGrow;

	public MutationBlockGrow(AbsoluteLocation location, boolean forceGrow)
	{
		_location = location;
		_forceGrow = forceGrow;
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
			if (_forceGrow)
			{
				PlantHelpers.performForcedGrow(env, context, _location, newBlock, block);
			}
			else
			{
				boolean shouldReschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(env, context, _location, newBlock, block);
				if (shouldReschedule)
				{
					context.mutationSink.future(this, MILLIS_BETWEEN_GROWTH_CALLS);
				}
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
		CodecHelpers.writeAbsoluteLocation(buffer, _location);
		CodecHelpers.writeBoolean(buffer, _forceGrow);
	}

	@Override
	public boolean canSaveToDisk()
	{
		// Common case.
		return true;
	}
}
