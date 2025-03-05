package com.jeffdisher.october.mutations;

import java.util.List;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IBlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.TickProcessingContext;


/**
 * Static helper methods for common multi-block idioms.
 */
public class MultiBlockUtils
{
	/**
	 * Looks up information related to the block shape at the given target location.
	 * 
	 * @param env The environment.
	 * @param context The current tick context.
	 * @param target The location to introspect.
	 * @return A description of the blocks there or null if not all the relevant blocks are loaded.
	 */
	public static Lookup getLoadedRoot(Environment env, TickProcessingContext context, AbsoluteLocation target)
	{
		// In this helper, we want to verify that all relevant proxies are loaded (only more than 1 if multi-block).
		Lookup lookup = null;
		BlockProxy outerProxy = context.previousBlockLookUp.apply(target);
		if (null != outerProxy)
		{
			// Find out if this is a multi-block.
			Block block = outerProxy.getBlock();
			if (env.blocks.isMultiBlock(block))
			{
				// This is a multi-block so verify that all proxies are loaded before returning the root proxy.
				AbsoluteLocation root = outerProxy.getMultiBlockRoot();
				if (null == root)
				{
					// We are the root.
					root = target;
				}
				BlockProxy rootProxy = context.previousBlockLookUp.apply(root);
				if (null != rootProxy)
				{
					boolean isLoaded = true;
					List<AbsoluteLocation> extensions = env.multiBlocks.getExtensions(block, root, rootProxy.getOrientation());
					for (AbsoluteLocation extension : extensions)
					{
						if (null == context.previousBlockLookUp.apply(extension))
						{
							isLoaded = false;
							break;
						}
					}
					if (isLoaded)
					{
						// Everything is loaded so we can use this.
						// (we return the root so later lookups can use it)
						lookup = new Lookup(root, rootProxy, extensions);
					}
				}
			}
			else
			{
				// This is the common case, just use the proxy we have.
				lookup = new Lookup(target, outerProxy, List.of());
			}
		}
		return lookup;
	}

	/**
	 * A convenience helper to schedule mutations against all the blocks in a multi-block (or single block).
	 * 
	 * @param context The current tick context.
	 * @param factory The factory to produce the mutations, given each block location.
	 * @param lookup The result of a previous lookup.
	 */
	public static void sendMutationToAll(TickProcessingContext context, Function<AbsoluteLocation, IMutationBlock> factory, Lookup lookup)
	{
		IMutationBlock mutation = factory.apply(lookup.rootLocation);
		context.mutationSink.next(mutation);
		for (AbsoluteLocation extension : lookup.extensions)
		{
			mutation = factory.apply(extension);
			context.mutationSink.next(mutation);
		}
	}

	/**
	 * Checks if the block described by this proxy is a multi-block extension.
	 * 
	 * @param env The environment.
	 * @param proxy The proxy to evaluate.
	 * @return True if this is both a multi-block and NOT the root of the multi-block (otherwise false).
	 */
	public static boolean isMultiBlockExtension(Environment env, IBlockProxy proxy)
	{
		return env.blocks.isMultiBlock(proxy.getBlock())
				&& (null != proxy.getMultiBlockRoot())
		;
	}


	/**
	 * Information to describe the shape of multi-block placements.  Note that extensions is never null but can be empty
	 * if this isn't a multi-block.
	 */
	public static record Lookup(AbsoluteLocation rootLocation, BlockProxy rootProxy, List<AbsoluteLocation> extensions)
	{}

}
