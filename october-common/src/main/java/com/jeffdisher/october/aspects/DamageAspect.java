package com.jeffdisher.october.aspects;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the damage aspect.
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

	public static final int[] TOUGHNESS_BY_TYPE = new int[ItemRegistry.ITEMS_BY_TYPE.length];

	static {
		// We construct the damage by looking at items.
		// This purely exists to demonstrate the shape of the Item data once it is fully external data:  All aspects are
		// out-of-line and their meaning for a specific Item type is answered through aspect-specific helpers, like this.
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < TOUGHNESS_BY_TYPE.length; ++i)
		{
			Item item = ItemRegistry.ITEMS_BY_TYPE[i];
			int toughness;
			if ((ItemRegistry.AIR == item)
					|| (ItemRegistry.WATER_SOURCE == item)
					|| (ItemRegistry.WATER_STRONG == item)
					|| (ItemRegistry.WATER_WEAK == item)
			)
			{
				toughness = UNBREAKABLE;
			}
			else if (ItemRegistry.CHARCOAL == item)
			{
				toughness = NOT_BLOCK;
			}
			else if ((ItemRegistry.LOG == item)
					|| (ItemRegistry.PLANK == item)
					|| (ItemRegistry.CRAFTING_TABLE == item)
					|| (ItemRegistry.DIRT == item)
			)
			{
				toughness = WEAK;
			}
			else if ((ItemRegistry.STONE == item)
					|| (ItemRegistry.STONE_BRICK == item)
					|| (ItemRegistry.FURNACE == item)
					|| (ItemRegistry.COAL_ORE == item)
			)
			{
				toughness = MEDIUM;
			}
			else if (ItemRegistry.IRON_ORE == item)
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

	public static int getToughness(Item item)
	{
		return TOUGHNESS_BY_TYPE[item.number()];
	}
}
