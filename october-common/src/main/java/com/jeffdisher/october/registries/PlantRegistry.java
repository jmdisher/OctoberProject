package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.Block;


/**
 * Represents the subset of Item objects related to plant life.
 * Some of these can grow or change into different items when planted or broken.
 */
public class PlantRegistry
{
	/**
	 * Returns the growth divisor to use when checking if this growth should happen.  Growth rate can be viewed as 1/x
	 * where x is the number returned from this function.  Returns 0 if this item doesn't have a concept of growth.
	 * 
	 * @param block The block to check.
	 * @return The divisor (0 if not growable).
	 */
	public static int growthDivisor(Block block)
	{
		int divisor;
		if (BlockAspect.SAPLING == block)
		{
			// Saplings grow 1/10th of the time.
			divisor = 10;
		}
		else if ((BlockAspect.WHEAT_SEEDLING == block)
				|| (BlockAspect.WHEAT_YOUNG == block)
		)
		{
			// Crops grow 1/2th of the time.
			divisor = 2;
		}
		else
		{
			// Return 0 if this can't grow.
			divisor = 0;
		}
		return divisor;
	}
}
