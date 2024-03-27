package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.registries.ItemRegistry;
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
		boolean didApply = false;
		// Make sure that this is a block which can grow.
		if (ItemRegistry.SAPLING == newBlock.getBlock().asItem())
		{
			// Replace this with a log and leaf blocks.
			// TODO:  Add some kind of randomness to this so that this is a "growth attempt".
			// TODO:  Figure out how to make more interesting trees.
			newBlock.setBlockAndClear(BlockAspect.getBlock(ItemRegistry.LOG));
			_tryScheduleLeaf(context, -1,  0,  0);
			_tryScheduleLeaf(context,  1,  0,  0);
			_tryScheduleLeaf(context,  0, -1,  0);
			_tryScheduleLeaf(context,  0,  1,  0);
			_tryScheduleLeaf(context,  0,  0,  1);
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

	private void _tryScheduleLeaf(TickProcessingContext context, int x, int y, int z)
	{
		AbsoluteLocation location = _location.getRelative(x, y, z);
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		if (null != proxy)
		{
			Block block = proxy.getBlock();
			if (block.canBeReplaced())
			{
				context.newMutationSink.accept(new MutationBlockOverwrite(location, BlockAspect.getBlock(ItemRegistry.LEAF)));
			}
		}
	}
}
