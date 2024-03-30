package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Item objects related to plant life.
 * Some of these can grow or change into different items when planted or broken.
 */
public class PlantRegistry
{
	/**
	 * Loads the plant growth config from the tablist in the given stream, sourcing Items from the given items registry.
	 * 
	 * @param items The existing ItemRegistry.
	 * @param blocks The existing BlockAspect.
	 * @param stream The stream containing the tablist describing growth divisors.
	 * @return The aspect (never null).
	 * @throws IOException There was a problem with a stream.
	 * @throws TabListReader.TabListException A tablist was malformed.
	 */
	public static PlantRegistry load(ItemRegistry items, BlockAspect blocks
			, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		FlatTabListCallbacks<Block, Integer> callbacks = new FlatTabListCallbacks<>(new FlatTabListCallbacks.BlockTransformer(items, blocks), new FlatTabListCallbacks.IntegerTransformer("opacity"));
		TabListReader.readEntireFile(callbacks, stream);
		
		// We can just pass these in, directly.
		return new PlantRegistry(callbacks.data);
	}
	private final Map<Block, Integer> _growthDivisors;

	private PlantRegistry(Map<Block, Integer> growthDivisors)
	{
		_growthDivisors = growthDivisors;
	}

	/**
	 * Returns the growth divisor to use when checking if this growth should happen.  Growth rate can be viewed as 1/x
	 * where x is the number returned from this function.  Returns 0 if this item doesn't have a concept of growth.
	 * 
	 * @param block The block to check.
	 * @return The divisor (0 if not growable).
	 */
	public int growthDivisor(Block block)
	{
		return _growthDivisors.containsKey(block)
				? _growthDivisors.get(block)
				: 0
		;
	}
}
