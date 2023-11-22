package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Inventory;


public class InventoryAspect
{
	public static final Aspect<Inventory> INVENTORY = new Aspect<>("Full Inventory", 1, Inventory.class);

	/**
	 * The encumbrance of an air block is small since it is just "on the ground" and we want containers to be used.
	 */
	public static final int AIR_BLOCK_ENCUMBRANCE = 10;
}
