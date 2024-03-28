package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;


/**
 * Contains constants and helpers associated with the damage aspect.
 * Since damage is only defined on blocks placed in the world, this aspect directly depends BlockAspect.
 */
public class DamageAspect
{
	/**
	 * We are limited to 15 bits to store the damage so we just fix the maximum at a round 32000.
	 */
	public static final short MAX_DAMAGE = 32000;

	/**
	 * The durability of items which CANNOT exist as blocks in the world.
	 */
	public static final short NOT_BLOCK = -1;

	/**
	 * Blocks which either can't be broken or don't make sense to break.
	 */
	public static final short UNBREAKABLE = 0;

	/**
	 * Very weak blocks which are trivial to break.
	 */
	public static final short TRIVIAL = 20;

	/**
	 * Common weak blocks.
	 */
	public static final short WEAK = 200;

	/**
	 * Common medium toughness blocks.
	 */
	public static final short MEDIUM = 2000;

	/**
	 * Common hard toughness blocks.
	 */
	public static final short HARD = 8000;

	/**
	 * Exceptionally strong blocks.
	 */
	public static final short STRONG = 20000;

	public static final short[] TOUGHNESS_BY_TYPE = new short[BlockAspect.BLOCKS_BY_TYPE.length];

	static {
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < TOUGHNESS_BY_TYPE.length; ++i)
		{
			Block block = BlockAspect.BLOCKS_BY_TYPE[i];
			short toughness;
			if (null == block)
			{
				// This is NOT a block which can exist in the world.
				toughness = NOT_BLOCK;
			}
			else if ((BlockAspect.AIR == block)
					|| (BlockAspect.WATER_SOURCE == block)
					|| (BlockAspect.WATER_STRONG == block)
					|| (BlockAspect.WATER_WEAK == block)
			)
			{
				toughness = UNBREAKABLE;
			}
			else if ((BlockAspect.SAPLING == block)
					|| (BlockAspect.LEAF == block)
					|| (BlockAspect.WHEAT_SEEDLING == block)
					|| (BlockAspect.WHEAT_YOUNG == block)
					|| (BlockAspect.WHEAT_MATURE == block)
			)
			{
				toughness = TRIVIAL;
			}
			else if ((BlockAspect.LOG == block)
					|| (BlockAspect.PLANK == block)
					|| (BlockAspect.CRAFTING_TABLE == block)
					|| (BlockAspect.DIRT == block)
					|| (BlockAspect.LANTERN == block)
			)
			{
				toughness = WEAK;
			}
			else if ((BlockAspect.STONE == block)
					|| (BlockAspect.STONE_BRICK == block)
					|| (BlockAspect.FURNACE == block)
					|| (BlockAspect.COAL_ORE == block)
			)
			{
				toughness = MEDIUM;
			}
			else if (BlockAspect.IRON_ORE == block)
			{
				toughness = HARD;
			}
			else
			{
				// We need to add this entry.
				// For now, just default them to medium.
				toughness = MEDIUM;
			}
			TOUGHNESS_BY_TYPE[i] = toughness;
		}
	}

	public static short getToughness(Block block)
	{
		return TOUGHNESS_BY_TYPE[block.item().number()];
	}
}
