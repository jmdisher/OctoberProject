package com.jeffdisher.october.aspects;

import java.io.IOException;

import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.utils.Assert;


/**
 * This is the root anchoring point for all the data loaded into aspects (or just registries, if there is no physical
 * aspect to store the data).  These aspects and registries can then be accessed via public instance variables.
 * This must be created during start-up so that the shared instance is available throughout the system, while running.
 * TODO:  In the future, this needs to be adapted to load the data files which describe these data types.
 */
public class Environment
{
	private static Environment INSTANCE;

	/**
	 * Can be called by any component, at any time, to access the environment.
	 * Asserts that the environment exists.
	 * 
	 * @return The shared instance of the environment.
	 */
	public static Environment getShared()
	{
		Assert.assertTrue(null != INSTANCE);
		return INSTANCE;
	}

	/**
	 * Creates the shared instance so that later calls can access it.
	 * Asserts that the environment does NOT exist.
	 * 
	 * @return The shared instance of the environment.
	 */
	public static Environment createSharedInstance()
	{
		Assert.assertTrue(null == INSTANCE);
		try
		{
			INSTANCE = new Environment();
		}
		catch (IOException | TabListException e)
		{
			// TODO:  Determine a better way to handle this.
			throw Assert.unexpected(e);
		}
		return INSTANCE;
	}

	/**
	 * Clears the existing environment shared instance.  This is primarily used in tests but should generally be used as
	 * a way of shutting down the system to verify everything has stopped.
	 * Asserts that the environment exists.
	 */
	public static void clearSharedInstance()
	{
		Assert.assertTrue(null != INSTANCE);
		INSTANCE = null;
	}

	public final ItemRegistry items;
	public final BlockAspect blocks;
	public final LiquidRegistry liquids;
	public final DurabilityAspect durability;
	public final InventoryEncumbrance encumbrance;
	public final CraftAspect crafting;
	public final DamageAspect damage;
	public final FuelAspect fuel;
	public final LightAspect lighting;
	public final PlantRegistry plants;
	public final FoodRegistry foods;
	public final ToolRegistry tools;
	public final ArmourRegistry armour;
	public final StationRegistry stations;
	public final LogicAspect logic;
	public final CreatureRegistry creatures;
	public final MultiBlockRegistry multiBlocks;
	public final GroundCoverRegistry groundCover;
	public final SpecialSlotAspect specialSlot;
	public final EnchantmentRegistry enchantments;
	public final SpecialConstants special;

	private Environment() throws IOException, TabListException
	{
		// Local instantiation only.
		ClassLoader loader = getClass().getClassLoader();
		this.items = ItemRegistry.loadRegistry(loader.getResourceAsStream("item_registry.tablist"));
		this.blocks = BlockAspect.loadRegistry(this.items
				, loader.getResourceAsStream("block_aspect.tablist")
				, loader.getResourceAsStream("block_aspect_A.tablist")
		);
		this.liquids = LiquidRegistry.loadRegistry(this.items, this.blocks, loader.getResourceAsStream("liquid_registry.tablist"));
		this.durability = DurabilityAspect.load(this.items, loader.getResourceAsStream("durability.tablist"));
		this.encumbrance = InventoryEncumbrance.load(this.items
				, loader.getResourceAsStream("inventory_encumbrance.tablist")
		);
		this.crafting = CraftAspect.load(this.items, this.blocks, this.encumbrance, loader.getResourceAsStream("crafting_recipes.tablist"));
		this.damage = DamageAspect.load(this.items, this.blocks, loader.getResourceAsStream("toughness.tablist"));
		this.fuel = FuelAspect.load(this.items, loader.getResourceAsStream("fuel_millis.tablist"));
		this.lighting = LightAspect.load(this.items, this.blocks
				, loader.getResourceAsStream("light_opacity.tablist")
				, loader.getResourceAsStream("light_sources.tablist")
				, loader.getResourceAsStream("light_sources_A.tablist")
		);
		this.plants = PlantRegistry.load(this.items, this.blocks, loader.getResourceAsStream("plant_registry.tablist"));
		this.foods = FoodRegistry.load(this.items, loader.getResourceAsStream("foods.tablist"));
		this.tools = ToolRegistry.load(this.items, loader.getResourceAsStream("tool_registry.tablist"));
		this.armour = ArmourRegistry.load(this.items, loader.getResourceAsStream("armour_registry.tablist"));
		this.stations = StationRegistry.load(this.items, this.blocks, this.crafting, loader.getResourceAsStream("station_registry.tablist"));
		this.logic = LogicAspect.load(this.items, this.blocks, loader.getResourceAsStream("logic.tablist"));
		this.creatures = CreatureRegistry.loadRegistry(this.items, loader.getResourceAsStream("creature_registry.tablist"));
		this.multiBlocks = new MultiBlockRegistry(this.items, this.blocks);
		this.groundCover = new GroundCoverRegistry(this.items, this.blocks);
		this.specialSlot = SpecialSlotAspect.load(this.items
			, this.blocks
			, loader.getResourceAsStream("special_slot.tablist")
		);
		this.enchantments = EnchantmentRegistry.load(this.items, this.blocks, this.durability, this.tools
			, loader.getResourceAsStream("enchanting.tablist")
			, loader.getResourceAsStream("infusions.tablist")
		);
		this.special = new SpecialConstants(this.items, this.blocks);
	}
}
