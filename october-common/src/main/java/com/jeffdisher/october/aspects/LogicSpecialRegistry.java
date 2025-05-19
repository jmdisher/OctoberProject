package com.jeffdisher.october.aspects;

import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.LogicLayerHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;


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
