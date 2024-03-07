package com.jeffdisher.october.aspects;

import com.jeffdisher.october.registries.ItemRegistry;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the fuel aspect.
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

	/**
	 * Used to check if the given item type has a fuel aspect.
	 * 
	 * @param item The item type to check.
	 * @return True if the given item type has a fuel inventory (fuel aspect).
	 */
	public static boolean doesHaveFuelInventory(Item item)
	{
		return _doesHaveFuelInventory(item);
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
	public static boolean hasFuelInventoryForType(Item blockType, Item fuelType)
	{
		return _doesHaveFuelInventory(blockType)
				&& (_millisOfFuel(fuelType) > 0)
		;
	}


	private static boolean _doesHaveFuelInventory(Item item)
	{
		return (ItemRegistry.FURNACE == item);
	}

	private static int _millisOfFuel(Item item)
	{
		int millis;
		// For now, we just store these constants inline and they will likely change and eventually become data.
		switch (item.number())
		{
		case BlockAspect.CRAFTING_TABLE:
			millis = BURN_MILLIS_TABLE;
			break;
		case BlockAspect.PLANK:
			millis = BURN_MILLIS_PLANK;
			break;
		case BlockAspect.LOG:
			millis = BURN_MILLIS_LOG;
			break;
		case BlockAspect.CHARCOAL:
			millis = BURN_MILLIS_CHARCOAL;
			break;
		default:
			millis = 0;
		}
		return millis;
	}
}
