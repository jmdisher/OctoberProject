package com.jeffdisher.october.logic;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers related to the dynamic in-game logic from switches, etc.
 */
public class LogicLayerHelpers
{
	public static Block blockTypeToPlace(TickProcessingContext context, AbsoluteLocation location, Block type)
	{
		// Check to see if we are placing a logic-sensitive block.
		Environment env = Environment.getShared();
		
		// Default to a normal block.
		Block placeType = type;
		if (env.logic.isSink(type))
		{
			// We always place a "low" type.
			Assert.assertTrue(!env.logic.isHigh(type));
			
			// See if we should replace this with the "high" version.
			if (_isHighState(env, context, location.getRelative(0, 0, -1))
					|| _isHighState(env, context, location.getRelative(0, 0, 1))
					|| _isHighState(env, context, location.getRelative(0, -1, 0))
					|| _isHighState(env, context, location.getRelative(0, 1, 0))
					|| _isHighState(env, context, location.getRelative(-1, 0, 0))
					|| _isHighState(env, context, location.getRelative(1, 0, 0))
			)
			{
				placeType = env.logic.getAlternate(type);
			}
		}
		return placeType;
	}


	private static boolean _isHighState(Environment env, TickProcessingContext context, AbsoluteLocation neighbour)
	{
		BlockProxy proxy = context.previousBlockLookUp.apply(neighbour);
		Block blockType = (null != proxy) ? proxy.getBlock() : null;
		return env.logic.isSource(blockType) && env.logic.isHigh(blockType);
	}
}
