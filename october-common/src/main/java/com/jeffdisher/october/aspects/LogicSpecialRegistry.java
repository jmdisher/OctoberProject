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
	public static final ISinkReceivingSignal GENERIC_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		return LogicLayerHelpers.isBlockReceivingHighSignal(env, proxyLookup, location);
	};

	/**
	 * A diode is a special case where it only checks the source "behind" (opposite its output) it.  Conduit or source.
	 */
	public static final ISinkReceivingSignal DIODE_SINK = (Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection) ->
	{
		AbsoluteLocation checkLocation;
		
		// We will invert the output direction to get this "input" direction.
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
		return LogicLayerHelpers.isEmittedLogicValueHigh(env, proxyLookup, location, checkLocation);
	};


	/**
	 * The interface implemented by special logic sinks to determine if they are receiving a signal from their
	 * surroundings.
	 * The reason this exists is that different logic blocks have different rules around which blocks can push logic
	 * into them and how they apply those states.
	 */
	public static interface ISinkReceivingSignal
	{
		/**
		 * Determines if the block would receive a high signal if placed in location, facing outputDirection.
		 * 
		 * @param env The environment.
		 * @param proxyLookup A look-up for that last tick's output proxies.
		 * @param location The location where the block is being placed.
		 * @param outputDirection The output direction of the block.
		 * @return True if it would receive a high signal here.
		 */
		public boolean isReceivingHighSignal(Environment env, Function<AbsoluteLocation, BlockProxy> proxyLookup, AbsoluteLocation location, OrientationAspect.Direction outputDirection);
	}
}
