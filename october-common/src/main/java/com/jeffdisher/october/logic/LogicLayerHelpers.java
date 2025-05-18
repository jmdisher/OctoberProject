package com.jeffdisher.october.logic;

import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;


/**
 * Helpers related to the dynamic in-game logic from switches, etc.
 */
public class LogicLayerHelpers
{
	/**
	 * These bits are used around BlockChangeDescription for communicating back which directions around the block should
	 * be checked when converting them into a target list.
	 */
	public static final byte LOGIC_BIT_THIS = 0x1;
	public static final byte LOGIC_BIT_EAST = 0x2;
	public static final byte LOGIC_BIT_WEST = 0x4;
	public static final byte LOGIC_BIT_NORTH = 0x8;
	public static final byte LOGIC_BIT_SOUTH = 0x10;
	public static final byte LOGIC_BIT_UP = 0x20;
	public static final byte LOGIC_BIT_DOWN = 0x40;

	public static boolean shouldSetActive(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, Block type)
	{
		// Only sinks can be set to active during placement (sources can be changed to active by a user).
		boolean isActive = false;
		
		// See if this is a logic block.
		if (env.logic.isAware(type))
		{
			// See if this is a sink which is receiving a signal.
			if (env.logic.isSink(type))
			{
				isActive = _isBlockReceivingHighSignal(env, proxyLookup, location);
			}
			else
			{
				// See if there is a special bit of logic for this block.
				isActive = env.logic.shouldPlaceAsActive(env, proxyLookup, location, type);
			}
		}
		return isActive;
	}

	public static boolean isBlockReceivingHighSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location)
	{
		return _isBlockReceivingHighSignal(env, proxyLookup, location);
	}

	public static boolean isBlockReceivingSourceSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location)
	{
		// A block receives a high signal if there is a >0 signal in an adjacent conduit or an adjacent block is actively outputting a signal into this block.
		return false
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative(0, 0, -1))
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative(0, 0,  1))
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative(0, -1, 0))
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative(0,  1, 0))
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative(-1, 0, 0))
				|| _getEmittedLogicValue(env, proxyLookup, true, location, location.getRelative( 1, 0, 0))
		;
	}

	public static void populateSetWithPotentialLogicChanges(Set<AbsoluteLocation> out_potentialLogicChangeSet, AbsoluteLocation blockLocation, byte logicBits)
	{
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_THIS))
		{
			out_potentialLogicChangeSet.add(blockLocation);
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_EAST))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(1, 0, 0));
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_WEST))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(-1, 0, 0));
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_NORTH))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(0, 1, 0));
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_SOUTH))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(0, -1, 0));
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_UP))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(0, 0, 1));
		}
		if (0x0 != (logicBits & LogicLayerHelpers.LOGIC_BIT_DOWN))
		{
			out_potentialLogicChangeSet.add(blockLocation.getRelative(0, 0, -1));
		}
	}


	private static boolean _isBlockReceivingHighSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location)
	{
		// A block receives a high signal if there is a >0 signal in an adjacent conduit or an adjacent block is actively outputting a signal into this block.
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
		boolean isValueHigh = false;
		
		// First, check that we can load the proxy (might be in a different cuboid which isn't loaded).
		BlockProxy proxy = proxyLookup.apply(checkLocation);
		if (null != proxy)
		{
			// Check the type:  If a conduit, just check the logic value.
			// Otherwise, if it is a source, just return true.
			Block type = proxy.getBlock();
			if (!sourceOnly && env.logic.isConduit(type))
			{
				isValueHigh = (proxy.getLogic() > 0);
			}
			else if (env.logic.isSource(type))
			{
				if (FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE))
				{
					// We only want to actually consider this source if it is facing into eventualTarget or is omni-directional.
					if (OrientationAspect.doesSingleBlockRequireOrientation(type))
					{
						OrientationAspect.Direction blockDirection = proxy.getOrientation();
						OrientationAspect.Direction requiredDirection = OrientationAspect.getRelativeDirection(checkLocation, eventualTarget);
						isValueHigh = (blockDirection == requiredDirection);
					}
					else
					{
						// Omni-directional.
						isValueHigh = true;
					}
				}
			}
		}
		return isValueHigh;
	}
}
