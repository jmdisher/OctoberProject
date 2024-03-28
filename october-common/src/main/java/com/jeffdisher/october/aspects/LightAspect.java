package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;


/**
 * Contains constants and helpers associated with the light aspect.
 * Note that all light is in-world, so this aspect is based directly on BlockAspect.
 */
public class LightAspect
{
	public static final byte MAX_LIGHT = 15;
	public static final byte OPAQUE = MAX_LIGHT;

	public static final byte[] OPACITY_BY_TYPE = new byte[BlockAspect.BLOCKS_BY_TYPE.length];

	static {
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < OPACITY_BY_TYPE.length; ++i)
		{
			Block block = BlockAspect.BLOCKS_BY_TYPE[i];
			byte opacity;
			if (BlockAspect.AIR == block)
			{
				opacity = 1;
			}
			else if ((BlockAspect.WATER_SOURCE == block)
					|| (BlockAspect.WATER_STRONG == block)
					|| (BlockAspect.WATER_WEAK == block)
					|| (BlockAspect.SAPLING == block)
					|| (BlockAspect.WHEAT_SEEDLING == block)
					|| (BlockAspect.WHEAT_YOUNG == block)
			)
			{
				opacity = 2;
			}
			else if ((BlockAspect.LEAF == block)
					|| (BlockAspect.WHEAT_MATURE == block)
			)
			{
				opacity = 4;
			}
			else
			{
				opacity = OPAQUE;
			}
			OPACITY_BY_TYPE[i] = opacity;
		}
	}

	/**
	 * Used to check the opacity of a block since light may only partially pass through it.  Note that all blocks have
	 * an opacity >= 1.
	 * 
	 * @param block The block type.
	 * @return The opacity value ([1..15]).
	 */
	public static byte getOpacity(Block block)
	{
		return OPACITY_BY_TYPE[block.asItem().number()];
	}

	/**
	 * Returns the light level emitted by this item type.
	 * 
	 * @param block The block type.
	 * @return The light level from this block ([0..15]).
	 */
	public static byte getLightEmission(Block block)
	{
		// Only the lantern currently emits light.
		return (BlockAspect.LANTERN == block)
				? MAX_LIGHT
				: 0
		;
	}
}
