package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

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
		int growthDivisor = env.plants.growthDivisor(block);
		if (growthDivisor > 0)
		{
			boolean shouldReschedule;
			boolean canGrow = _forceGrow;
			if (!canGrow)
			{
				// See if the random generator says we should grow this tick or try again later.
				int randomBits = context.randomInt.applyAsInt(growthDivisor);
				canGrow = (1 == randomBits);
			}
			if (canGrow)
			{
				Block nextPhase = env.plants.nextPhaseForPlant(block);
				if (null != nextPhase)
				{
					// Become that next phase.
					newBlock.setBlockAndClear(nextPhase);
					// Reschedule if that block is also growable.
					shouldReschedule = (env.plants.growthDivisor(nextPhase) > 0);
				}
				else if (env.plants.isTree(block))
				{
					_growTree(context, newBlock);
					shouldReschedule = false;
				}
				else
				{
					// This shouldn't be possible since we must be growing into something.
					throw Assert.unreachable();
				}
			}
			else
			{
				// Just reschedule this.
				shouldReschedule = true;
			}
			
			if (shouldReschedule && !_forceGrow)
			{
				context.mutationSink.future(this, MILLIS_BETWEEN_GROWTH_CALLS);
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


	private void _growTree(TickProcessingContext context, IMutableBlockProxy newBlock)
	{
		Environment env = Environment.getShared();
		Block log = env.blocks.fromItem(env.items.getItemById("op.log"));
		Block leaf = env.blocks.fromItem(env.items.getItemById("op.leaf"));
		// Replace this with a log and leaf blocks.
		// TODO:  Figure out how to make more interesting trees.
		newBlock.setBlockAndClear(log);
		_tryScheduleBlockOverwrite(context, env,  log,  0,  0,  1);
		_tryScheduleBlockOverwrite(context, env, leaf, -1,  0,  1);
		_tryScheduleBlockOverwrite(context, env, leaf,  1,  0,  1);
		_tryScheduleBlockOverwrite(context, env, leaf,  0, -1,  1);
		_tryScheduleBlockOverwrite(context, env, leaf,  0,  1,  1);
	}

	private void _tryScheduleBlockOverwrite(TickProcessingContext context, Environment env, Block blockType, int x, int y, int z)
	{
		AbsoluteLocation location = _location.getRelative(x, y, z);
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		if (null != proxy)
		{
			Block block = proxy.getBlock();
			if (env.blocks.canBeReplaced(block))
			{
				context.mutationSink.next(new MutationBlockOverwrite(location, blockType));
			}
		}
	}
}
