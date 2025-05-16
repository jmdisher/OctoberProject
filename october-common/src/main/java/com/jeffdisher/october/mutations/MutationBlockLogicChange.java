package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.logic.LogicLayerHelpers;
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
		if (env.logic.isAware(thisBlock) && env.logic.isSink(thisBlock) && !MultiBlockUtils.isMultiBlockExtension(env, newBlock))
		{
			if (LogicLayerHelpers.isBlockReceivingHighSignal(env, context.previousBlockLookUp, _blockLocation))
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


	private static void _sendReplaceWithAlternate(Environment env, TickProcessingContext context, AbsoluteLocation rootBlockLocation, IBlockProxy proxy)
	{
		Block initial = proxy.getBlock();
		Block alternate = env.logic.getAlternate(initial);
		
		MultiBlockUtils.Lookup lookup = MultiBlockUtils.getLoadedRoot(env, context, rootBlockLocation);
		MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
			MutationBlockInternalSetLogicState mutation = new MutationBlockInternalSetLogicState(location, initial, alternate);
			return mutation;
		}, lookup);
	}
}
