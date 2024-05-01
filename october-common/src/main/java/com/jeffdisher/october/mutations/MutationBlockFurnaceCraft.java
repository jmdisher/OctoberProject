package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.CraftingBlockSupport;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Called by MutationBlockStoreItems or itself in order to advance the crafting progress of a furnace.
 * Note that this relies on the ephemeral object for the block so that only one of these applied and potentially
 * rescheduled per tick.
 */
public class MutationBlockFurnaceCraft implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.CRAFT_IN_FURNACE;

	public static MutationBlockFurnaceCraft deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockFurnaceCraft(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockFurnaceCraft(AbsoluteLocation blockLocation)
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
		// TODO:  Stop using this constant once we pass this tick time through the context.
		long craftMillisRemaining = 100L;
		CraftingBlockSupport.FueledResult result = CraftingBlockSupport.runFueled(Environment.getShared(), newBlock, craftMillisRemaining);
		if (result.shouldReschedule())
		{
			context.mutationSink.next(new MutationBlockFurnaceCraft(_blockLocation));
		}
		return result.didApply();
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
