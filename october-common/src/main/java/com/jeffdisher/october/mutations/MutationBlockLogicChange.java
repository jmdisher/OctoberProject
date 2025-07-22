package com.jeffdisher.october.mutations;

import java.nio.ByteBuffer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LogicAspect;
import com.jeffdisher.october.data.IMutableBlockProxy;
import com.jeffdisher.october.net.CodecHelpers;
import com.jeffdisher.october.net.DeserializationContext;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * These mutations are directly synthesized by the system, itself, and scheduled on blocks adjacent to those with logic
 * changes.
 * Internally, all it does is check if the receiving block can act on the signal change and then schedules
 * MutationBlockInternalSetLogicState mutations against all logically connected blocks (to account for multi-blocks).
 */
public class MutationBlockLogicChange implements IMutationBlock
{
	public static final MutationBlockType TYPE = MutationBlockType.LOGIC_CHANGE;

	public static MutationBlockLogicChange deserialize(DeserializationContext context)
	{
		ByteBuffer buffer = context.buffer();
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
		LogicAspect.ISignalChangeCallback logicHandler = env.logic.logicUpdateHandler(thisBlock);
		if ((null != logicHandler) && !MultiBlockUtils.isMultiBlockExtension(env, newBlock))
		{
			boolean isActive = FlagsAspect.isSet(newBlock.getFlags(), FlagsAspect.FLAG_ACTIVE);
			if (logicHandler.shouldStoreHighSignal(env, context.previousBlockLookUp, _blockLocation, newBlock.getOrientation()))
			{
				// This is set high so switch to the corresponding "high".
				if (!isActive)
				{
					_sendReplaceWithAlternate(env, context, _blockLocation, true);
					didApply = true;
				}
			}
			else
			{
				// This is set low so switch to the corresponding "low".
				if (isActive)
				{
					_sendReplaceWithAlternate(env, context, _blockLocation, false);
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


	private static void _sendReplaceWithAlternate(Environment env, TickProcessingContext context, AbsoluteLocation rootBlockLocation, boolean setHigh)
	{
		MultiBlockUtils.Lookup lookup = MultiBlockUtils.getLoadedRoot(env, context, rootBlockLocation);
		MultiBlockUtils.sendMutationToAll(context, (AbsoluteLocation location) -> {
			MutationBlockInternalSetLogicState mutation = new MutationBlockInternalSetLogicState(location, setHigh);
			return mutation;
		}, lookup);
	}
}
