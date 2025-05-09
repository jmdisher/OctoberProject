package com.jeffdisher.october.logic;

import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * Helpers related to the dynamic in-game logic from switches, etc.
 */
public class LogicLayerHelpers
{
	public static Block blockTypeToPlace(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, Block type)
	{
		// Check to see if we are placing a logic-sensitive block.
		
		// Default to a normal block.
		Block placeType = type;
		if (env.logic.isSink(type))
		{
			// We always place a "low" type.
			Assert.assertTrue(!env.logic.isHigh(type));
			
			// See if we should replace this with the "high" version.
			if (_isBlockReceivingHighSignal(env, proxyLookup, location))
			{
				placeType = env.logic.getAlternate(type);
			}
		}
		return placeType;
	}

	public static boolean isBlockReceivingHighSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location)
	{
		return _isBlockReceivingHighSignal(env, proxyLookup, location);
	}


	private static boolean _isBlockReceivingHighSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location)
	{
		return false
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative(0, 0, -1))
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative(0, 0,  1))
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative(0, -1, 0))
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative(0,  1, 0))
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative(-1, 0, 0))
				|| _getEmittedLogicValue(env, proxyLookup, false, location, location.getRelative( 1, 0, 0))
		;
	}

	private static boolean _getEmittedLogicValue(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, boolean sourceOnly, AbsoluteLocation eventualTarget, AbsoluteLocation checkLocation)
	{
		BlockProxy proxy = proxyLookup.apply(checkLocation);
		byte value = (null != proxy)
				? proxy.getLogic()
				: 0
		;
		return (value > 0);
	}
}
