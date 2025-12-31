package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * A list of special references into the Environment data which are required for core mechanics.
 */
public class SpecialConstants
{
	public static final String KEY_ITEM_ARROW = "item_arrow";
	public static final String KEY_ITEM_PORTAL_ORB = "item_portal_orb";
	public static final String KEY_ITEM_FERTILIZER = "item_fertilizer";
	public static final String KEY_ITEM_STONE_HOE = "item_stone_hoe";

	public static final String KEY_BLOCK_AIR = "block_air";
	public static final String KEY_BLOCK_HOPPER = "block_hopper";
	public static final String KEY_BLOCK_LOG = "block_log";
	public static final String KEY_BLOCK_LEAF = "block_leaf";
	public static final String KEY_BLOCK_PORTAL_KEYSTONE = "block_portal_keystone";
	public static final String KEY_BLOCK_PORTAL_SURFACE = "block_portal_surface";
	public static final String KEY_BLOCK_CUBOID_LOADER = "block_cuboid_loader";
	public static final String KEY_BLOCK_BED = "block_bed";
	public static final String KEY_BLOCK_DIRT = "block_dirt";
	public static final String KEY_BLOCK_GRASS = "block_grass";
	public static final String KEY_BLOCK_TILLED_SOIL = "block_tilled_soil";

	public static SpecialConstants load(ItemRegistry items
		, BlockAspect blocks
		, InputStream stream
	) throws IOException, TabListReader.TabListException
	{
		if (null == stream)
		{
			throw new IOException("Resource missing");
		}
		FlatTabListCallbacks<String, Item> callbacks = new FlatTabListCallbacks<>((String value) -> value, new IValueTransformer.ItemTransformer(items));
		TabListReader.readEntireFile(callbacks, stream);
		
		// We need to process the mapping with our constants to make sure that we have everything we expected.
		Map<String, Item> map = new HashMap<>(callbacks.data);
		Item itemArrow = _removeAsItem(map, KEY_ITEM_ARROW);
		Item itemPortalOrb = _removeAsItem(map, KEY_ITEM_PORTAL_ORB);
		Item itemFertilizer = _removeAsItem(map, KEY_ITEM_FERTILIZER);
		Item itemStoneHoe = _removeAsItem(map, KEY_ITEM_STONE_HOE);
		
		Block blockAir = _removeAsBlock(blocks, map, KEY_BLOCK_AIR);
		Block blockHopper = _removeAsBlock(blocks, map, KEY_BLOCK_HOPPER);
		Block blockLog = _removeAsBlock(blocks, map, KEY_BLOCK_LOG);
		Block blockLeaf = _removeAsBlock(blocks, map, KEY_BLOCK_LEAF);
		Block blockPortalKeystone = _removeAsBlock(blocks, map, KEY_BLOCK_PORTAL_KEYSTONE);
		Block blockPortalSurface = _removeAsBlock(blocks, map, KEY_BLOCK_PORTAL_SURFACE);
		Block blockCuboidLoader = _removeAsBlock(blocks, map, KEY_BLOCK_CUBOID_LOADER);
		Block blockBed = _removeAsBlock(blocks, map, KEY_BLOCK_BED);
		Block blockDirt = _removeAsBlock(blocks, map, KEY_BLOCK_DIRT);
		Block blockGrass = _removeAsBlock(blocks, map, KEY_BLOCK_GRASS);
		Block blockTilledSoil = _removeAsBlock(blocks, map, KEY_BLOCK_TILLED_SOIL);
		
		// If anything else is here, it is an error.
		for (String key : map.keySet())
		{
			throw new TabListReader.TabListException("Extraneous key: \"" + key + "\"");
		}
		
		return new SpecialConstants(itemArrow
			, itemPortalOrb
			, itemFertilizer
			, itemStoneHoe
			
			, blockAir
			, blockHopper
			, blockLog
			, blockLeaf
			, blockPortalKeystone
			, blockPortalSurface
			, blockCuboidLoader
			, blockBed
			, blockDirt
			, blockGrass
			, blockTilledSoil
		);
	}


	/**
	 * Required to generate item when a projectile hits a surface.
	 * This could be removed if the item type were included in the projectile data, somewhere.
	 */
	public final Item itemArrow;
	/**
	 * Has special use in portal logic.
	 */
	public final Item itemPortalOrb;
	/**
	 * Special "use on block" case.
	 */
	public final Item itemFertilizer;
	/**
	 * Special "use on block" case.
	 */
	public final Item itemStoneHoe;

	/**
	 * Air is a default "empty" block in many areas so it is defined here.
	 */
	public final Block AIR;
	/**
	 * Hopper mechanics are currently special.
	 */
	public final Block blockHopper;
	/**
	 * Used in plant growth logic.
	 */
	public final Block blockLog;
	/**
	 * Used in plant growth logic.
	 */
	public final Block blockLeaf;
	/**
	 * Has special use in portal logic.
	 */
	public final Block blockPortalKeystone;
	/**
	 * Has special use in portal logic.
	 */
	public final Block blockPortalSurface;
	/**
	 * This is a special one-off utility.
	 */
	public final Block blockCuboidLoader;
	/**
	 * Has special behaviour with setting spawn and changing time.
	 */
	public final Block blockBed;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockDirt;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockGrass;
	/**
	 * Special "use on block" case.
	 */
	public final Block blockTilledSoil;

	private SpecialConstants(Item itemArrow
		, Item itemPortalOrb
		, Item itemFertilizer
		, Item itemStoneHoe
		
		, Block blockAir
		, Block blockHopper
		, Block blockLog
		, Block blockLeaf
		, Block blockPortalKeystone
		, Block blockPortalSurface
		, Block blockCuboidLoader
		, Block blockBed
		, Block blockDirt
		, Block blockGrass
		, Block blockTilledSoil
	)
	{
		this.itemArrow = itemArrow;
		this.itemPortalOrb = itemPortalOrb;
		this.itemFertilizer = itemFertilizer;
		this.itemStoneHoe = itemStoneHoe;
		
		this.AIR = blockAir;
		this.blockHopper = blockHopper;
		this.blockLog = blockLog;
		this.blockLeaf = blockLeaf;
		this.blockPortalKeystone = blockPortalKeystone;
		this.blockPortalSurface = blockPortalSurface;
		this.blockCuboidLoader = blockCuboidLoader;
		this.blockBed = blockBed;
		this.blockDirt = blockDirt;
		this.blockGrass = blockGrass;
		this.blockTilledSoil = blockTilledSoil;
	}


	private static Block _removeAsBlock(BlockAspect blocks, Map<String, Item> data, String key) throws TabListReader.TabListException
	{
		Item item = _removeAsItem(data, key);
		Block block = blocks.fromItem(item);
		if (null == block)
		{
			throw new TabListReader.TabListException("Valid item is not block: \"" + key + "\"");
		}
		return block;
	}

	private static Item _removeAsItem(Map<String, Item> data, String key) throws TabListReader.TabListException
	{
		Item value = data.remove(key);
		if (null == value)
		{
			throw new TabListReader.TabListException("Missing key: \"" + key + "\"");
		}
		return value;
	}
}
