package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


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
		if (env.logic.isAware(thisBlock) && env.logic.isSink(thisBlock) && !MultiBlockUtils.isMultiBlockExtension(env, newBlock))
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
					_sendReplaceWithAlternate(env, context, _blockLocation, newBlock);
					didApply = true;
				}
			}
			else
			{
				// This is set low so switch to the corresponding "low".
				if (env.logic.isHigh(thisBlock))
				{
					_sendReplaceWithAlternate(env, context, _blockLocation, newBlock);
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
		byte value = (null != proxy)
				? proxy.getLogic()
				: 0
		;
		return (value > 0);
	}

	private static void _sendReplaceWithAlternate(Environment env, TickProcessingContext context, AbsoluteLocation location, IBlockProxy proxy)
	{
		Block initial = proxy.getBlock();
		Block alternate = env.logic.getAlternate(initial);
		context.mutationSink.next(new MutationBlockReplace(location, initial, alternate));
		
		// See if this is a multi-block type and update extensions.
		if (env.blocks.isMultiBlock(initial))
		{
			// The alternate must also be multi.
			Assert.assertTrue(env.blocks.isMultiBlock(alternate));
			OrientationAspect.Direction direction = proxy.getOrientation();
			for (AbsoluteLocation extension : env.multiBlocks.getExtensions(initial, location, direction))
			{
				context.mutationSink.next(new MutationBlockReplace(extension, initial, alternate));
			}
		}
	}
}
