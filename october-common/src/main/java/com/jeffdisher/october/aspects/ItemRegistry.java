package com.jeffdisher.october.aspects;

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
	private List<Item> _items = new ArrayList<>();

	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public final Item AIR = _register("Air");
	public final Item STONE = _register("Stone");
	public final Item LOG = _register("Log");
	public final Item PLANK = _register("Plank");
	public final Item STONE_BRICK = _register("Stone Brick");
	public final Item CRAFTING_TABLE = _register("Crafting Table");
	public final Item FURNACE = _register("Furnace");
	public final Item CHARCOAL = _register("Charcoal");
	public final Item COAL_ORE = _register("Coal Ore");
	public final Item IRON_ORE = _register("Iron Ore");
	public final Item DIRT = _register("Dirt");
	public final Item WATER_SOURCE = _register("Water Source");
	public final Item WATER_STRONG = _register("Water (Strong Flow)");
	public final Item WATER_WEAK = _register("Water (Weak Flow)");
	public final Item LANTERN = _register("Lantern");
	public final Item IRON_INGOT = _register("Iron ingot");
	public final Item SAPLING = _register("Sapling");
	public final Item LEAF = _register("Leaf");
	public final Item WHEAT_SEED = _register("Wheat seed");
	public final Item WHEAT_ITEM = _register("Wheat item");
	public final Item WHEAT_SEEDLING = _register("Wheat seedling");
	public final Item WHEAT_YOUNG = _register("Young wheat");
	public final Item WHEAT_MATURE = _register("Mature wheat");

	private Item _register(String name)
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
	public final Item[] ITEMS_BY_TYPE;

	public ItemRegistry()
	{
		// Convert the items into the array as it is now fixed (since the static fields are initialized before this is called).
		ITEMS_BY_TYPE = _items.toArray((int size) -> new Item[size]);
		_items = null;
	}
}
