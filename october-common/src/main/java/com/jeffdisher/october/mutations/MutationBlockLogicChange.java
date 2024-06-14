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
	// TODO:  Replace this with something generalized into the broader logic aspect design.
	private static final String STRING_DOOR_CLOSED = "op.door_closed";
	private static final String STRING_DOOR_OPEN = "op.door_open";
	private static final String STRING_SWITCH_ON = "op.switch_on";

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
		// TODO:  Generalize this lookup into data.
		Block doorClosed = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_CLOSED));
		Block doorOpen = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_OPEN));
		Block switchOn = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_ON));
		Block thisBlock = newBlock.getBlock();
		
		boolean didApply = false;
		if ((doorClosed == thisBlock) || (doorOpen == thisBlock))
		{
			// This block is sensitive so check the surrounding blocks to see what they are emitting.
			if (_getEmittedLogicValue(context, _blockLocation.getRelative(0, 0, -1), switchOn)
					|| _getEmittedLogicValue(context, _blockLocation.getRelative(0, 0, 1), switchOn)
					|| _getEmittedLogicValue(context, _blockLocation.getRelative(0, -1, 0), switchOn)
					|| _getEmittedLogicValue(context, _blockLocation.getRelative(0, 1, 0), switchOn)
					|| _getEmittedLogicValue(context, _blockLocation.getRelative(-1, 0, 0), switchOn)
					|| _getEmittedLogicValue(context, _blockLocation.getRelative(1, 0, 0), switchOn)
					)
			{
				// This is set high so make sure that we are the open door.
				if (doorOpen != thisBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_blockLocation, thisBlock, doorOpen));
					didApply = true;
				}
			}
			else
			{
				// This is set low so make sure that we are the closed door.
				if (doorClosed != thisBlock)
				{
					context.mutationSink.next(new MutationBlockReplace(_blockLocation, thisBlock, doorClosed));
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


	private static boolean _getEmittedLogicValue(TickProcessingContext context, AbsoluteLocation location, Block switchOn)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(location);
		Block block = (null != proxy) ? proxy.getBlock() : null;
		return (switchOn == block);
	}
}
