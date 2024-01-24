package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


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
	public static final Item LOG = new Item(BlockAspect.LOG, 2);
	public static final Item PLANK = new Item(BlockAspect.PLANK, 1);
	public static final Item STONE_BRICK = new Item(BlockAspect.STONE_BRICK, 2);

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public static final Item[] BLOCKS_BY_TYPE = {
			AIR,
			STONE,
			LOG,
			PLANK,
			STONE_BRICK,
	};

	static {
		// Validate that the registry is internally consistent.
		for (int i = 0; i < BLOCKS_BY_TYPE.length; ++i)
		{
			Assert.assertTrue((short)i == BLOCKS_BY_TYPE[i].number());
		}
	}

	private ItemRegistry()
	{
		// No instantiation.
	}
}
