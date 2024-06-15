package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are sent to blocks which receive logic signals, when the signal changes.
 */
public class MutationBlockLogicChange implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.LOGIC_CHANGE;

	public static MutationBlockLogicChange deserializeFromBuffer(ByteBuffer buffer)
	{
		AbsoluteLocation location = CodecHelpers.readAbsoluteLocation(buffer);
		return new MutationBlockLogicChange(location);
	}


	private final AbsoluteLocation _blockLocation;

	public MutationBlockLogicChange(AbsoluteLocation blockLocation)
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
		// Check to see if this block is sensitive to logic changes.
		Environment env = Environment.getShared();
		Block thisBlock = newBlock.getBlock();
		
		boolean didApply = false;
		if (env.logic.isAware(thisBlock) && env.logic.isSink(thisBlock))
		{
			// This block is sensitive so check the surrounding blocks to see what they are emitting.
			if (_getEmittedLogicValue(env, context, _blockLocation.getRelative(0, 0, -1))
					|| _getEmittedLogicValue(env, context, _blockLocation.getRelative(0, 0, 1))
					|| _getEmittedLogicValue(env, context, _blockLocation.getRelative(0, -1, 0))
					|| _getEmittedLogicValue(env, context, _blockLocation.getRelative(0, 1, 0))
					|| _getEmittedLogicValue(env, context, _blockLocation.getRelative(-1, 0, 0))
					|| _getEmittedLogicValue(env, context, _blockLocation.getRelative(1, 0, 0))
					)
			{
				// This is set high so switch to the corresponding "high".
				if (!env.logic.isHigh(thisBlock))
				{
					Block alternate = env.logic.getAlternate(thisBlock);
					context.mutationSink.next(new MutationBlockReplace(_blockLocation, thisBlock, alternate));
					didApply = true;
				}
			}
			else
			{
				// This is set low so switch to the corresponding "low".
				if (env.logic.isHigh(thisBlock))
				{
					Block alternate = env.logic.getAlternate(thisBlock);
					context.mutationSink.next(new MutationBlockReplace(_blockLocation, thisBlock, alternate));
					didApply = true;
				}
			}
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


	private static boolean _getEmittedLogicValue(Environment env, TickProcessingContext context, AbsoluteLocation location)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		Block block = (null != proxy) ? proxy.getBlock() : null;
		return env.logic.isSource(block) && env.logic.isHigh(block);
	}
}
