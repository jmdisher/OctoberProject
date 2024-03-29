package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
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
	/**
	 * Loads the registry from the tablist in the given stream.
	 * The format for the item registry tablist is simple, where each line has the form:
	 * ID<TAB>NAME
	 * 
	 * @param stream The stream containing the tablist.
	 * @return The registry (never null).
	 * @throws IOException There was a problem with the stream.
	 * @throws TabListException The tablist was malformed.
	 */
	public static ItemRegistry loadRegistry(InputStream stream) throws IOException, TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		List<String> ids = new ArrayList<>();
		List<String> names = new ArrayList<>();
		boolean[] isValid = new boolean[] { true };
		TabListReader.readEntireFile(new TabListReader.IParseCallbacks() {
			@Override
			public void startNewRecord(String name)
			{
				ids.add(name);
			}
			@Override
			public void handleParameter(String value)
			{
				names.add(value);
			}
			@Override
			public void endRecord()
			{
				if (ids.size() != names.size())
				{
					isValid[0] = false;
				}
			}
		}, stream);
		
		if (!isValid[0] || (ids.size() != names.size()))
		{
			throw new TabListException("ItemRegistry tablist malformed");
		}
		return new ItemRegistry(ids, names);
	}


	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public final Item AIR;
	public final Item STONE;
	public final Item LOG;
	public final Item PLANK;
	public final Item STONE_BRICK;
	public final Item CRAFTING_TABLE;
	public final Item FURNACE;
	public final Item CHARCOAL;
	public final Item COAL_ORE;
	public final Item IRON_ORE;
	public final Item DIRT;
	public final Item WATER_SOURCE;
	public final Item WATER_STRONG;
	public final Item WATER_WEAK;
	public final Item LANTERN;
	public final Item IRON_INGOT;
	public final Item SAPLING;
	public final Item LEAF;
	public final Item WHEAT_SEED;
	public final Item WHEAT_ITEM;
	public final Item WHEAT_SEEDLING;
	public final Item WHEAT_YOUNG;
	public final Item WHEAT_MATURE;

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public final Item[] ITEMS_BY_TYPE;
	private final Map<String, Item> _idsMap;

	private ItemRegistry(List<String> ids, List<String> names)
	{
		// Local instantiation only.
		int size = ids.size();
		Assert.assertTrue(size <= Short.MAX_VALUE);
		this.ITEMS_BY_TYPE = new Item[size];
		_idsMap = new HashMap<>();
		for (int i = 0; i < size; ++i)
		{
			Item item = new Item(names.get(i), (short)i);
			this.ITEMS_BY_TYPE[i] = item;
			_idsMap.put(ids.get(i), item);
		}
		
		this.AIR = _idsMap.get("op.air");
		this.STONE = _idsMap.get("op.stone");
		this.LOG = _idsMap.get("op.log");
		this.PLANK = _idsMap.get("op.plank");
		this.STONE_BRICK = _idsMap.get("op.stone_brick");
		this.CRAFTING_TABLE = _idsMap.get("op.crafting_table");
		this.FURNACE = _idsMap.get("op.furnace");
		this.CHARCOAL = _idsMap.get("op.charcoal");
		this.COAL_ORE = _idsMap.get("op.coal_ore");
		this.IRON_ORE = _idsMap.get("op.iron_ore");
		this.DIRT = _idsMap.get("op.dirt");
		this.WATER_SOURCE = _idsMap.get("op.water_source");
		this.WATER_STRONG = _idsMap.get("op.water_strong");
		this.WATER_WEAK = _idsMap.get("op.water_weak");
		this.LANTERN = _idsMap.get("op.lantern");
		this.IRON_INGOT = _idsMap.get("op.iron_ingot");
		this.SAPLING = _idsMap.get("op.sapling");
		this.LEAF = _idsMap.get("op.leaf");
		this.WHEAT_SEED = _idsMap.get("op.wheat_seed");
		this.WHEAT_ITEM = _idsMap.get("op.wheat_item");
		this.WHEAT_SEEDLING = _idsMap.get("op.wheat_seedling");
		this.WHEAT_YOUNG = _idsMap.get("op.wheat_young");
		this.WHEAT_MATURE = _idsMap.get("op.wheat_mature");
	}
}