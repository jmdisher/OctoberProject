package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.Item;


/**
 * Items are defined as constants, and this is where they are all created and looked up.
 */
public class ItemRegistry
{
	// Note that every item has a number and encumbrance, 0 is used for any item which cannot exist in an inventory.
	public static final int NON_INVENTORY = 0;

	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public static final Item AIR = new Item(BlockAspect.AIR, NON_INVENTORY);
	public static final Item STONE = new Item(BlockAspect.STONE, 2);


	private ItemRegistry()
	{
		// No instantiation.
	}
}
