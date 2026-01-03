package com.jeffdisher.october.aspects;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.utils.Assert;


/**
 * This is the root anchoring point for all the data loaded into aspects (or just registries, if there is no physical
 * aspect to store the data).  These aspects and registries can then be accessed via public instance variables.
 * This must be created during start-up so that the shared instance is available throughout the system, while running.
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
	 * @throws IOException Error accessing local resources.
	 * @throws TabListReader.TabListException Local resources have invalid definition.
	 */
	public static Environment createSharedInstance() throws IOException, TabListReader.TabListException
	{
		Assert.assertTrue(null == INSTANCE);
		INSTANCE = new Environment();
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
	public final CompositeRegistry composites;
	public final GroundCoverRegistry groundCover;
	public final SpecialSlotAspect specialSlot;
	public final EnchantmentRegistry enchantments;
	public final OrientationAspect orientations;
	public final SpecialConstants special;

	private Environment() throws IOException, TabListReader.TabListException
	{
		// Local instantiation only.
		ClassLoader loader = getClass().getClassLoader();
		try (InputStream itemStream = loader.getResourceAsStream("item_registry.tablist"))
		{
			this.items = ItemRegistry.loadRegistry(itemStream);
		}
		try (InputStream blockStream = loader.getResourceAsStream("block_aspect.tablist");
			InputStream blockActiveStream = loader.getResourceAsStream("block_aspect_A.tablist")
		)
		{
			this.blocks = BlockAspect.loadRegistry(this.items
				, blockStream
				, blockActiveStream
			);
		}
		try (InputStream liquidStream = loader.getResourceAsStream("liquid_registry.tablist"))
		{
			this.liquids = LiquidRegistry.loadRegistry(this.items
				, this.blocks
				, liquidStream
			);
		}
		try (InputStream durabilityStream = loader.getResourceAsStream("durability.tablist"))
		{
			this.durability = DurabilityAspect.load(this.items
				, durabilityStream
			);
		}
		try (InputStream durabilityStream = loader.getResourceAsStream("inventory_encumbrance.tablist"))
		{
			this.encumbrance = InventoryEncumbrance.load(this.items
				, durabilityStream
			);
		}
		try (InputStream recipeStream = loader.getResourceAsStream("crafting_recipes.tablist"))
		{
			this.crafting = CraftAspect.load(this.items
				, this.blocks
				, this.encumbrance
				, recipeStream
			);
		}
		try (InputStream toughnessStream = loader.getResourceAsStream("toughness.tablist"))
		{
			this.damage = DamageAspect.load(this.items
				, this.blocks
				, toughnessStream
			);
		}
		try (InputStream fuelStream = loader.getResourceAsStream("fuel_millis.tablist"))
		{
			this.fuel = FuelAspect.load(this.items
				, fuelStream
			);
		}
		try (InputStream opacityStream = loader.getResourceAsStream("light_opacity.tablist");
			InputStream sourceStream = loader.getResourceAsStream("light_sources.tablist");
			InputStream sourceActiveStream = loader.getResourceAsStream("light_sources_A.tablist")
		)
		{
			this.lighting = LightAspect.load(this.items
				, this.blocks
				, opacityStream
				, sourceStream
				, sourceActiveStream
			);
		}
		try (InputStream plantStream = loader.getResourceAsStream("plant_registry.tablist"))
		{
			this.plants = PlantRegistry.load(this.items
				, this.blocks
				, plantStream
			);
		}
		try (InputStream foodStream = loader.getResourceAsStream("foods.tablist"))
		{
			this.foods = FoodRegistry.load(this.items
				, foodStream
			);
		}
		try (InputStream toolStream = loader.getResourceAsStream("tool_registry.tablist"))
		{
			this.tools = ToolRegistry.load(this.items
				, toolStream
			);
		}
		try (InputStream armourStream = loader.getResourceAsStream("armour_registry.tablist"))
		{
			this.armour = ArmourRegistry.load(this.items
				, armourStream
			);
		}
		try (InputStream stationStream = loader.getResourceAsStream("station_registry.tablist"))
		{
			this.stations = StationRegistry.load(this.items
				, this.blocks
				, this.crafting
				, stationStream
			);
		}
		try (InputStream logicStream = loader.getResourceAsStream("logic.tablist"))
		{
			this.logic = LogicAspect.load(this.items
				, this.blocks
				, logicStream
			);
		}
		try (InputStream creatureStream = loader.getResourceAsStream("creature_registry.tablist"))
		{
			this.creatures = CreatureRegistry.loadRegistry(this.items
				, creatureStream
			);
		}
		try (InputStream multiBlockStream = loader.getResourceAsStream("multi_block_registry.tablist"))
		{
			this.multiBlocks = MultiBlockRegistry.load(this.items
				, this.blocks
				, multiBlockStream
			);
		}
		try (InputStream compositeStream = loader.getResourceAsStream("composite_registry.tablist"))
		{
			this.composites = CompositeRegistry.load(this.items
				, this.blocks
				, compositeStream
			);
		}
		try (InputStream groundCoverStream = loader.getResourceAsStream("ground_cover_registry.tablist"))
		{
			this.groundCover = GroundCoverRegistry.load(this.items
				, this.blocks
				, groundCoverStream
			);
		}
		try (InputStream specialSlotStream = loader.getResourceAsStream("special_slot.tablist"))
		{
			this.specialSlot = SpecialSlotAspect.load(this.items
				, this.blocks
				, specialSlotStream
			);
		}
		try (InputStream enchantingStream = loader.getResourceAsStream("enchanting.tablist");
			InputStream infusionsStream = loader.getResourceAsStream("infusions.tablist");
		)
		{
			this.enchantments = EnchantmentRegistry.load(this.items
				, this.blocks
				, this.durability
				, this.tools
				, enchantingStream
				, infusionsStream
			);
		}
		try (InputStream orientationStream = loader.getResourceAsStream("orientation_aspect.tablist"))
		{
			this.orientations = OrientationAspect.load(this.items
				, this.blocks
				, orientationStream
			);
		}
		try (InputStream specialConstantsStream = loader.getResourceAsStream("special_constants.tablist"))
		{
			this.special = SpecialConstants.load(this.items
				, this.blocks
				, specialConstantsStream
			);
		}
	}
}
