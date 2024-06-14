package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.MutationBlockLogicChange;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Helpers related to the dynamic in-game logic from switches, etc.
 */
public class LogicLayerHelpers
{
	// TODO:  Remove this once we generalize the block logic aspect.
	private static final String STRING_SWITCH_ON = "op.switch_on";
	private static final String STRING_DOOR_OPEN = "op.door_open";
	private static final String STRING_DOOR_CLOSED = "op.door_closed";

	public static void blockWasReplaced(TickProcessingContext context, AbsoluteLocation location, Block oldBlock, Block newBlock)
	{
		Environment env = Environment.getShared();
		// If we removed an on switch or added one, run the logic.
		Block switchOn = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_SWITCH_ON));
		if ((switchOn == oldBlock) || (switchOn == newBlock))
		{
			Block doorOpen = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_OPEN));
			Block doorClosed = env.blocks.getAsPlaceableBlock(env.items.getItemById(STRING_DOOR_CLOSED));
			// We broke a logic source so emit the logic change event to the adjacent blocks if they are logic sinks.
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(0, 0, -1));
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(0, 0, 1));
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(0, -1, 0));
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(0, 1, 0));
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(-1, 0, 0));
			_checkSendLogicUpdate(context, doorOpen, doorClosed, location.getRelative(1, 0, 0));
		}
	}


	private static void _checkSendLogicUpdate(TickProcessingContext context, Block doorOpen, Block doorClosed, AbsoluteLocation neighbour)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(neighbour);
		Block blockType = (null != proxy) ? proxy.getBlock() : null;
		if ((doorOpen == blockType) || (doorClosed == blockType))
		{
			MutationBlockLogicChange logicChange = new MutationBlockLogicChange(neighbour);
			context.mutationSink.next(logicChange);
		}
	}
}
