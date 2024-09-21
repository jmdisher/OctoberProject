package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.HopperHelpers;
import com.jeffdisher.october.logic.PlantHelpers;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Applies a mutation to a given location in response to a call into schedulePeriodicMutation() (in
 * TickProcessingContext).
 * An example use-case of this is plant growth, as this is scheduled periodically.
 */
public class MutationBlockPeriodic implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.PERIODIC;
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;
	public static final long MILLIS_BETWEEN_HOPPER_CALLS = 1_000L;

	public static MutationBlockPeriodic deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockPeriodic(location);
	}


	private final AbsoluteLocation _location;

	public MutationBlockPeriodic(AbsoluteLocation location)
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
			boolean shouldReschedule = PlantHelpers.shouldRescheduleAfterPlantPeriodic(env, context, _location, newBlock, block);
			
			if (shouldReschedule)
			{
				newBlock.requestFutureMutation(MILLIS_BETWEEN_GROWTH_CALLS);
			}
			didApply = true;
		}
		else if (HopperHelpers.isHopper(_location, newBlock))
		{
			HopperHelpers.tryProcessHopper(context, _location, newBlock);
			newBlock.requestFutureMutation(MILLIS_BETWEEN_HOPPER_CALLS);
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
