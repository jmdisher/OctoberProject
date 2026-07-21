package com.jeffdisher.october.types;


/**
 * This interface describes the read-only parts of MutableSlotManager which are used in cases where an external viewer
 * wants to interpret the slots of a player.
 */
public interface ISlotManager
{
	/**
	 * A basic helper to check how many items of a given type are in the inventory.  This cannot be called if the item
	 * is non-stackable.
	 * 
	 * @param stackable The item type.
	 * @return The number of items of this type.
	 */
	public int getCount(Item stackable);
}
