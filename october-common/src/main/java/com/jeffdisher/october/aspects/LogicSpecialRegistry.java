package com.jeffdisher.october.aspects;

import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Some logic sources double as sinks and have non-trivial functions to map from input to output so their
 * implementations are stored here.
 * Note that this does create a bizarre dependency between packages (ultimately, the LogicAspect depending on high-level
 * components) so it may be changed into some kind of injected implementation, later on.
 */
public class LogicSpecialRegistry
{
	/**
	 * The common case of sinks, they just check all 6 adjacent blocks to see if any are high (conduit or source).
	 */
	public static final LogicAspect.ISignalChangeCallback GENERIC_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		return LogicLayerHelpers.isBlockReceivingHighSignal(env, proxyLookup, location);
	};

	/**
	 * A diode is a special case where it only checks the source "behind" (opposite its output) it.  Conduit or source.
	 */
	public static final LogicAspect.ISignalChangeCallback DIODE_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		AbsoluteLocation checkLocation = _getInputOpposite(location, outputDirection);
		return LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, checkLocation);
	};

	/**
	 * A AND gate is a special case where it only checks the source "left" and "right" of the "output".  Conduit or
	 * source.
	 */
	public static final LogicAspect.ISignalChangeCallback AND_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		AbsoluteLocation[] leftRight = _getInputLeftRight(location, outputDirection);
		boolean left = LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, leftRight[0]);
		boolean right = LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, leftRight[1]);
		return left && right;
	};

	/**
	 * A OR gate is a special case where it only checks the source "left" and "right" of the "output".  Conduit or
	 * source.
	 */
	public static final LogicAspect.ISignalChangeCallback OR_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		AbsoluteLocation[] leftRight = _getInputLeftRight(location, outputDirection);
		boolean left = LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, leftRight[0]);
		boolean right = LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, leftRight[1]);
		return left || right;
	};

	/**
	 * A NOT gate is a special case where it only checks the source "behind" (opposite its output) it.  Conduit or source.
	 */
	public static final LogicAspect.ISignalChangeCallback NOT_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		AbsoluteLocation checkLocation = _getInputOpposite(location, outputDirection);
		return !LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, checkLocation);
	};

	/**
	 * Emitters are always outputting a signal.
	 */
	public static final LogicAspect.ISignalChangeCallback EMITTER = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		return true;
	};


	private static AbsoluteLocation _getInputOpposite(AbsoluteLocation location, OrientationAspect.Direction outputDirection)
	{
		AbsoluteLocation checkLocation;
		switch(outputDirection)
		{
		case NORTH:
			// Check south.
			checkLocation = location.getRelative(0, -1, 0);
			break;
		case WEST:
			// Check east.
			checkLocation = location.getRelative(1, 0, 0);
			break;
		case SOUTH:
			// Check north.
			checkLocation = location.getRelative(0, 1, 0);
			break;
		case EAST:
			// Check west.
			checkLocation = location.getRelative(-1, 0, 0);
			break;
		default:
			// This case is not yet supported.
			throw Assert.unreachable();
		}
		return checkLocation;
	}

	private static AbsoluteLocation[] _getInputLeftRight(AbsoluteLocation location, OrientationAspect.Direction outputDirection)
	{
		AbsoluteLocation checkLeft;
		AbsoluteLocation checkRight;
		
		switch(outputDirection)
		{
		case NORTH:
			// Check WEST, EAST.
			checkLeft = location.getRelative(-1, 0, 0);
			checkRight = location.getRelative(1, 0, 0);
			break;
		case WEST:
			// Check SOUTH, NORTH.
			checkLeft = location.getRelative(0, -1, 0);
			checkRight = location.getRelative(0, 1, 0);
			break;
		case SOUTH:
			// Check EAST, WEST.
			checkLeft = location.getRelative(1, 0, 0);
			checkRight = location.getRelative(-1, 0, 0);
			break;
		case EAST:
			// Check NORTH, SOUTH.
			checkLeft = location.getRelative(0, 1, 0);
			checkRight = location.getRelative(0, -1, 0);
			break;
		default:
			// This case is not yet supported.
			throw Assert.unreachable();
		}
		return new AbsoluteLocation[] { checkLeft, checkRight };
	}
}
