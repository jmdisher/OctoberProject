package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the fuel aspect.
 * Note that blocks which can be fuelled and contain a fuel inventory are Block instances while the actual fuel items
 * don't need to be placed in the world so they are Item instances.
 */
public class FuelAspect
{
	/**
	 * We just use this constant for the fuel capacity.
	 */
	public static final int CAPACITY = 10;

	/**
	 * The time length of various burns.
	 */
	public static final int BURN_MILLIS_TABLE = 8000;
	public static final int BURN_MILLIS_PLANK = 2000;
	public static final int BURN_MILLIS_LOG = 4000;
	public static final int BURN_MILLIS_CHARCOAL = 20000;
	public static final int BURN_MILLIS_SAPLING = 500;

	public static final int[] BURN_MILLIS_BY_TYPE = new int[ItemRegistry.ITEMS_BY_TYPE.length];

	static {
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < BURN_MILLIS_BY_TYPE.length; ++i)
		{
			Item item = ItemRegistry.ITEMS_BY_TYPE[i];
			int millis;
			// For now, we just store these constants inline and they will likely change and eventually become data.
			if (ItemRegistry.CRAFTING_TABLE == item)
			{
				millis = BURN_MILLIS_TABLE;
			}
			else if (ItemRegistry.PLANK == item)
			{
				millis = BURN_MILLIS_PLANK;
			}
			else if (ItemRegistry.LOG == item)
			{
				millis = BURN_MILLIS_LOG;
			}
			else if ((ItemRegistry.CHARCOAL == item)
					|| (ItemRegistry.COAL_ORE == item)
			)
			{
				millis = BURN_MILLIS_CHARCOAL;
			}
			else if (ItemRegistry.SAPLING == item)
			{
				millis = BURN_MILLIS_SAPLING;
			}
			else
			{
				// We assume other things don't burn.
				millis = 0;
			}
			BURN_MILLIS_BY_TYPE[i] = millis;
		}
	}

	/**
	 * Used to check if the given block type has a fuel aspect.
	 * 
	 * @param block The block type to check.
	 * @return True if the given block type has a fuel inventory (fuel aspect).
	 */
	public static boolean doesHaveFuelInventory(Block block)
	{
		return _doesHaveFuelInventory(block);
	}

	/**
	 * Used to determine how much fuel to apply when consuming an item or just to check if an item is valid in a fuel
	 * slot (non-fuel items will return 0).
	 * 
	 * @param item The item type to check.
	 * @return The milliseconds of fuel produced by consuming this item or 0 if it isn't a fuel type.
	 */
	public static int millisOfFuel(Item item)
	{
		return _millisOfFuel(item);
	}

	/**
	 * A helper to address the common idiom of needing to determine if there is a fuel inventory for a given block type
	 * when trying to load it with a given fuel type.
	 * This helper is pretty specific so it may move elsewhere or be deleted in the future.
	 * 
	 * @param blockType The block type to check for fuel aspect.
	 * @param fuelType The item type to check as a valid fuel for this block.
	 * @return True if this block type has a fuel aspect and can use the given fuel type.
	 */
	public static boolean hasFuelInventoryForType(Block blockType, Item fuelType)
	{
		return _doesHaveFuelInventory(blockType)
				&& (_millisOfFuel(fuelType) > 0)
		;
	}


	private static boolean _doesHaveFuelInventory(Block block)
	{
		// We just inline this case since it is only one but will be generalized later.
		return (BlockAspect.FURNACE == block);
	}

	private static int _millisOfFuel(Item item)
	{
		return BURN_MILLIS_BY_TYPE[item.number()];
	}
}
