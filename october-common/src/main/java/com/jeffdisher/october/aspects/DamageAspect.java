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

	private final short[] _toughnessByBlockType;

	public DamageAspect(BlockAspect blocks)
	{
		_toughnessByBlockType = new short[blocks.BLOCKS_BY_TYPE.length];
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < _toughnessByBlockType.length; ++i)
		{
			Block block = blocks.BLOCKS_BY_TYPE[i];
			short toughness;
			if (null == block)
			{
				// This is NOT a block which can exist in the world.
				toughness = NOT_BLOCK;
			}
			else if ((blocks.AIR == block)
					|| (blocks.WATER_SOURCE == block)
					|| (blocks.WATER_STRONG == block)
					|| (blocks.WATER_WEAK == block)
			)
			{
				toughness = UNBREAKABLE;
			}
			else if ((blocks.SAPLING == block)
					|| (blocks.LEAF == block)
					|| (blocks.WHEAT_SEEDLING == block)
					|| (blocks.WHEAT_YOUNG == block)
					|| (blocks.WHEAT_MATURE == block)
			)
			{
				toughness = TRIVIAL;
			}
			else if ((blocks.LOG == block)
					|| (blocks.PLANK == block)
					|| (blocks.CRAFTING_TABLE == block)
					|| (blocks.DIRT == block)
					|| (blocks.LANTERN == block)
			)
			{
				toughness = WEAK;
			}
			else if ((blocks.STONE == block)
					|| (blocks.STONE_BRICK == block)
					|| (blocks.FURNACE == block)
					|| (blocks.COAL_ORE == block)
			)
			{
				toughness = MEDIUM;
			}
			else if (blocks.IRON_ORE == block)
			{
				toughness = HARD;
			}
			else
			{
				// We need to add this entry.
				// For now, just default them to medium.
				toughness = MEDIUM;
			}
			_toughnessByBlockType[i] = toughness;
		}
	}

	public short getToughness(Block block)
	{
		return _toughnessByBlockType[block.item().number()];
	}
}
