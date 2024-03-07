package com.jeffdisher.october.registries;

import com.jeffdisher.october.aspects.BlockAspect;
import com.jeffdisher.october.aspects.DamageAspect;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Items are defined as constants, and this is where they are all created and looked up.
 * At the moment, items here are defined as a composition of other aspects but it isn't clear that this is the correct
 * direction of relationship (it is convenient and simple, yet seems conceptually backward).
 * Note that the ItemRegistry will eventually be loaded from data, once we have a sense of what kind of information is
 * required.
 */
public class ItemRegistry
{
	// Note that every item has a number and encumbrance, 0 is used for any item which cannot exist in an inventory.
	public static final int NON_INVENTORY = 0;

	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public static final Item AIR = new Item("Air"
			, BlockAspect.AIR
			, NON_INVENTORY
			, DamageAspect.UNBREAKABLE
	);
	public static final Item STONE = new Item("Stone"
			, BlockAspect.STONE
			, 2
			, DamageAspect.MEDIUM
	);
	public static final Item LOG = new Item("Log"
			, BlockAspect.LOG
			, 2
			, DamageAspect.WEAK
	);
	public static final Item PLANK = new Item("Plank"
			, BlockAspect.PLANK
			, 1
			, DamageAspect.WEAK
	);
	public static final Item STONE_BRICK = new Item("Stone Brick"
			, BlockAspect.STONE_BRICK
			, 2
			, DamageAspect.MEDIUM
	);
	public static final Item CRAFTING_TABLE = new Item("Crafting Table"
			, BlockAspect.CRAFTING_TABLE
			, 2
			, DamageAspect.WEAK
	);
	public static final Item FURNACE = new Item("Furnace"
			, BlockAspect.FURNACE
			, 4
			, DamageAspect.MEDIUM
	);
	public static final Item CHARCOAL = new Item("Charcoal"
			, BlockAspect.CHARCOAL
			, 4
			, DamageAspect.NOT_BLOCK
	);

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public static final Item[] ITEMS_BY_TYPE = {
			AIR,
			STONE,
			LOG,
			PLANK,
			STONE_BRICK,
			CRAFTING_TABLE,
			FURNACE,
			CHARCOAL,
	};

	static {
		// Validate that the registry is internally consistent.
		for (int i = 0; i < ITEMS_BY_TYPE.length; ++i)
		{
			Assert.assertTrue((short)i == ITEMS_BY_TYPE[i].number());
		}
	}

	private ItemRegistry()
	{
		// No instantiation.
	}
}
