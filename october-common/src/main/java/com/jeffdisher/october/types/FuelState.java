package com.jeffdisher.october.types;


/**
 * The fuel state of a block.  This is used for things like furnaces, etc.
 * The general idea is that the inventory stores items which can specifically be consumed as "fuel".  When doing so, a
 * "fuel value" will be added to millisFuelled, which will then be applied for fuelled crafting operations, eventually
 * draining back to 0.
 * NOTE:
 * -millisFuelled MUST be >= 0 (we use -1 as "null", in the codec, since it doesn't make sense in a real object)
 * -fuelInventory CAN be null, since it can be faked-up dynamically based on the block type
 * Technically, the FuelState could be stored as null and faked-up dynamically if it was only going to be 0 millis with
 * an empty inventory.  This could be useful in keeping data small.
 * Note that "currentFuel" is only non-null if "millisFuelled" is >0.
 */
public record FuelState(int millisFuelled
		, Item currentFuel
		, Inventory fuelInventory
)
{
	/**
	 * Used to determine if this is empty and can therefore be replaced with a null, in storage.
	 * 
	 * @return True if this fuel state has no meaningful information.
	 */
	public boolean isEmpty()
	{
		return (0 == millisFuelled) && (0 == fuelInventory.currentEncumbrance);
	}
}
