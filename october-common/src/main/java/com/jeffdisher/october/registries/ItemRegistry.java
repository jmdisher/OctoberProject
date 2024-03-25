package com.jeffdisher.october.registries;

import java.util.ArrayList;
import java.util.List;

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
	// Variables used when initially populating the table.
	private static List<Item> _items = new ArrayList<>();

	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public static final Item AIR = _register("Air");
	public static final Item STONE = _register("Stone");
	public static final Item LOG = _register("Log");
	public static final Item PLANK = _register("Plank");
	public static final Item STONE_BRICK = _register("Stone Brick");
	public static final Item CRAFTING_TABLE = _register("Crafting Table");
	public static final Item FURNACE = _register("Furnace");
	public static final Item CHARCOAL = _register("Charcoal");
	public static final Item COAL_ORE = _register("Coal Ore");
	public static final Item IRON_ORE = _register("Iron Ore");
	public static final Item DIRT = _register("Dirt");
	public static final Item WATER_SOURCE = _register("Water Source");
	public static final Item WATER_STRONG = _register("Water (Strong Flow)");
	public static final Item WATER_WEAK = _register("Water (Weak Flow)");
	public static final Item LANTERN = _register("Lantern");
	public static final Item IRON_INGOT = _register("Iron ingot");

	private static Item _register(String name)
	{
		int size = _items.size();
		Assert.assertTrue(size <= Short.MAX_VALUE);
		short number = (short)size;
		Item item = new Item(name, number);
		_items.add(item);
		return item;
	}

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public static final Item[] ITEMS_BY_TYPE;

	static {
		// Convert the items into the array as it is now fixed (since the static fields are initialized before this is called).
		ITEMS_BY_TYPE = _items.toArray((int size) -> new Item[size]);
		_items = null;
	}

	private ItemRegistry()
	{
		// No instantiation.
	}
}
