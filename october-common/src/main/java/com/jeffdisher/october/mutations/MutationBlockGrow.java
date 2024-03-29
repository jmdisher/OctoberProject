package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.function.IntSupplier;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Attempts to apply "growth" to the given block, potentially replacing it with a mature plant or scheduling a future
 * growth operation.
 */
public class MutationBlockGrow implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.GROW;
	public static final long MILLIS_BETWEEN_GROWTH_CALLS = 10_000L;
	/**
	 * The random provider is non-final since tests can replace it.
	 */
	public static IntSupplier RANDOM_PROVIDER;

	static {
		Random randomObject = new Random();
		RANDOM_PROVIDER = () ->
		{
			return randomObject.nextInt();
		};
	}

	public static MutationBlockGrow deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockGrow(location);
	}


	private final AbsoluteLocation _location;

	public MutationBlockGrow(AbsoluteLocation location)
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
		int growthDivisor = env.plants.growthDivisor(block);
		if (growthDivisor > 0)
		{
			boolean shouldReschedule;
			// See if the random generator says we should grow this tick or try again later.
			int randomBits = RANDOM_PROVIDER.getAsInt();
			if (1 == (randomBits % growthDivisor))
			{
				if (env.blocks.SAPLING == block)
				{
					_growTree(context, newBlock);
					shouldReschedule = false;
				}
				else
				{
					// In other cases, just advance to the next stage.
					shouldReschedule = _growCrop(context, newBlock, block);
				}
			}
			else
			{
				// Just reschedule this.
				shouldReschedule = true;
			}
			
			if (shouldReschedule)
			{
				context.delatedMutationSink.accept(this, MILLIS_BETWEEN_GROWTH_CALLS);
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
	}


	private void _growTree(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		// Replace this with a log and leaf blocks.
		// TODO:  Figure out how to make more interesting trees.
		newBlock.setBlockAndClear(env.blocks.LOG);
		_tryScheduleLeaf(context, -1,  0,  0);
		_tryScheduleLeaf(context,  1,  0,  0);
		_tryScheduleLeaf(context,  0, -1,  0);
		_tryScheduleLeaf(context,  0,  1,  0);
		_tryScheduleLeaf(context,  0,  0,  1);
	}

	private void _tryScheduleLeaf(TickProcessingContext context, int x, int y, int z)
	{
		Environment env = Environment.getShared();
		AbsoluteLocation location = _location.getRelative(x, y, z);
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		if (null != proxy)
		{
			Block block = proxy.getBlock();
			if (env.blocks.canBeReplaced(block))
			{
				context.newMutationSink.accept(new MutationBlockOverwrite(location, env.blocks.LEAF));
			}
		}
	}

	private boolean _growCrop(TickProcessingContext context, IMutableBlockProxy newBlock, Block block)
	{
		Environment env = Environment.getShared();
		boolean shouldReschedule;
		if (env.blocks.WHEAT_SEEDLING == block)
		{
			newBlock.setBlockAndClear(env.blocks.WHEAT_YOUNG);
			shouldReschedule = true;
		}
		else if (env.blocks.WHEAT_YOUNG == block)
		{
			newBlock.setBlockAndClear(env.blocks.WHEAT_MATURE);
			shouldReschedule = false;
		}
		else
		{
			// This is a missing piece of data.
			throw Assert.unreachable();
		}
		return shouldReschedule;
	}
}
