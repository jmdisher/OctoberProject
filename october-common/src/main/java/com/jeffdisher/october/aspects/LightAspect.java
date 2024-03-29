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

	private final BlockAspect _blocks;
	private final byte[] _opacityByBlockType;

	public LightAspect(BlockAspect blocks)
	{
		_blocks = blocks;
		_opacityByBlockType = new byte[blocks.BLOCKS_BY_TYPE.length];
		
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < _opacityByBlockType.length; ++i)
		{
			Block block = blocks.BLOCKS_BY_TYPE[i];
			byte opacity;
			if (blocks.AIR == block)
			{
				opacity = 1;
			}
			else if ((blocks.WATER_SOURCE == block)
					|| (blocks.WATER_STRONG == block)
					|| (blocks.WATER_WEAK == block)
					|| (blocks.SAPLING == block)
					|| (blocks.WHEAT_SEEDLING == block)
					|| (blocks.WHEAT_YOUNG == block)
			)
			{
				opacity = 2;
			}
			else if ((blocks.LEAF == block)
					|| (blocks.WHEAT_MATURE == block)
			)
			{
				opacity = 4;
			}
			else
			{
				opacity = OPAQUE;
			}
			_opacityByBlockType[i] = opacity;
		}
	}

	/**
	 * Used to check the opacity of a block since light may only partially pass through it.  Note that all blocks have
	 * an opacity >= 1.
	 * 
	 * @param block The block type.
	 * @return The opacity value ([1..15]).
	 */
	public byte getOpacity(Block block)
	{
		return _opacityByBlockType[block.item().number()];
	}

	/**
	 * Returns the light level emitted by this item type.
	 * 
	 * @param block The block type.
	 * @return The light level from this block ([0..15]).
	 */
	public byte getLightEmission(Block block)
	{
		// Only the lantern currently emits light.
		return (_blocks.LANTERN == block)
				? MAX_LIGHT
				: 0
		;
	}
}
