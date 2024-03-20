package com.jeffdisher.october.aspects;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the light aspect.
 */
public class LightAspect
{
	public static final short MAX_LIGHT = 15;
	public static final short OPAQUE = MAX_LIGHT;

	/**
	 * Used to check the opacity of a block since light may only partially pass through it.  Note that all blocks have
	 * an opacity >= 1.
	 * 
	 * @param item The block type.
	 * @return The opacity value ([1..15]).
	 */
	public static short getOpacity(Item item)
	{
		short opacity;
		if (ItemRegistry.AIR == item)
		{
			opacity = 1;
		}
		else if ((ItemRegistry.WATER_SOURCE == item)
				|| (ItemRegistry.WATER_STRONG == item)
				|| (ItemRegistry.WATER_WEAK == item)
		)
		{
			opacity = 2;
		}
		else
		{
			opacity = OPAQUE;
		}
		return opacity;
	}
}
