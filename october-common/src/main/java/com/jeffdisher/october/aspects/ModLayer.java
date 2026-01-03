package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Contains the extracted ZIP contents of a mod ZIP.  Only the expected file name keys are exposed.  The intention is
 * that instances of this only exist during start-up.
 */
public class ModLayer
{
	public static final String FILE_ITEM_REGISTRY = "item_registry.tablist";
	public static final String FILE_BLOCK_ASPECT = "block_aspect.tablist";
	public static final String FILE_BLOCK_ASPECT_ACTIVE = "block_aspect_A.tablist";
	public static final String FILE_LIQUID_REGISTRY = "liquid_registry.tablist";
	public static final String FILE_DURABILITY = "durability.tablist";
	public static final String FILE_INVENTORY_ENCUMBRANCE = "inventory_encumbrance.tablist";
	public static final String FILE_CRAFTING_RECIPES = "crafting_recipes.tablist";
	public static final String FILE_TOUGHNESS = "toughness.tablist";
	public static final String FILE_FUEL_MILLIS = "fuel_millis.tablist";
	public static final String FILE_LIGHT_OPACITY = "light_opacity.tablist";
	public static final String FILE_LIGHT_SOURCES = "light_sources.tablist";
	public static final String FILE_LIGHT_SOURCE_ACTIVE = "light_sources_A.tablist";
	public static final String FILE_PLANT_REGISTRY = "plant_registry.tablist";
	public static final String FILE_FOODS = "foods.tablist";
	public static final String FILE_TOOL_REGISTRY = "tool_registry.tablist";
	public static final String FILE_ARMOUR_REGISTRY = "armour_registry.tablist";
	public static final String FILE_STATION_REGISTRY = "station_registry.tablist";
	public static final String FILE_LOGIC = "logic.tablist";
	public static final String FILE_CRETURE_REGISTRY = "creature_registry.tablist";
	public static final String FILE_MULTI_BLOCK_REGISTRY = "multi_block_registry.tablist";
	public static final String FILE_COMPOSITE_REGISTRY = "composite_registry.tablist";
	public static final String FILE_GROUND_COVER_REGISTRY = "ground_cover_registry.tablist";
	public static final String FILE_SPECIAL_SLOT = "special_slot.tablist";
	public static final String FILE_ENCHANTING = "enchanting.tablist";
	public static final String FILE_INFUSIONS = "infusions.tablist";
	public static final String FILE_ORIENTATION_ASPECT = "orientation_aspect.tablist";
	public static final String FILE_SPECIAL_CONSTANTS = "special_constants.tablist";
	public static final Set<String> MOD_FILE_NAMES;

	static {
		Set<String> set = new HashSet<>();
		set.add(FILE_ITEM_REGISTRY);
		set.add(FILE_BLOCK_ASPECT);
		set.add(FILE_BLOCK_ASPECT_ACTIVE);
		set.add(FILE_LIQUID_REGISTRY);
		set.add(FILE_DURABILITY);
		set.add(FILE_INVENTORY_ENCUMBRANCE);
		set.add(FILE_CRAFTING_RECIPES);
		set.add(FILE_TOUGHNESS);
		set.add(FILE_FUEL_MILLIS);
		set.add(FILE_LIGHT_OPACITY);
		set.add(FILE_LIGHT_SOURCES);
		set.add(FILE_LIGHT_SOURCE_ACTIVE);
		set.add(FILE_PLANT_REGISTRY);
		set.add(FILE_FOODS);
		set.add(FILE_TOOL_REGISTRY);
		set.add(FILE_ARMOUR_REGISTRY);
		set.add(FILE_STATION_REGISTRY);
		set.add(FILE_LOGIC);
		set.add(FILE_CRETURE_REGISTRY);
		set.add(FILE_MULTI_BLOCK_REGISTRY);
		set.add(FILE_COMPOSITE_REGISTRY);
		set.add(FILE_GROUND_COVER_REGISTRY);
		set.add(FILE_SPECIAL_SLOT);
		set.add(FILE_ENCHANTING);
		set.add(FILE_INFUSIONS);
		set.add(FILE_ORIENTATION_ASPECT);
		set.add(FILE_SPECIAL_CONSTANTS);
		MOD_FILE_NAMES = Collections.unmodifiableSet(set);
	}

	/**
	 * Loads a LodLayer from the given ZipInputStream, logging any files being ignored.
	 * 
	 * @param stream The zipped mod files.
	 * @return A new ModLayer.
	 * @throws IOException There was a problem reading from the stream.
	 */
	public static ModLayer load(ZipInputStream stream) throws IOException
	{
		Map<String, byte[]> contents = new HashMap<>();
		ZipEntry entry;
		while (null != (entry = stream.getNextEntry()))
		{
			String name = entry.getName();
			if (MOD_FILE_NAMES.contains(name))
			{
				byte[] bytes = stream.readAllBytes();
				contents.put(name, bytes);
			}
			else
			{
				System.out.println("Ingoring unknown file in mod zip: \"" + name + "\"");
			}
		}
		return new ModLayer(contents);
	}


	public final Map<String, byte[]> contents;

	private ModLayer(Map<String, byte[]> contents)
	{
		this.contents = Collections.unmodifiableMap(contents);
	}
}
