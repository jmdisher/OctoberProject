package com.jeffdisher.october.aspects;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.LayeredInputStream;


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
		INSTANCE = new Environment(new ModLayer[0]);
		return INSTANCE;
	}

	/**
	 * Create the shared instance with the given layers of mods on top of the base installation.
	 * Asserts that the environment does NOT exist.
	 * 
	 * @param layers The mods to load on top of the base installation.
	 * @return The shared instance of the environment.
	 * @throws IOException Error accessing local resources.
	 * @throws TabListReader.TabListException Local resources have invalid definition.
	 */
	public static Environment createModdedInstance(ModLayer[] layers) throws IOException, TabListReader.TabListException
	{
		Assert.assertTrue(null == INSTANCE);
		INSTANCE = new Environment(layers);
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

	private Environment(ModLayer[] mods) throws IOException, TabListReader.TabListException
	{
		// Local instantiation only.
		ClassLoader loader = getClass().getClassLoader();
		try (InputStream itemStream = _openModded(loader, mods, ModLayer.FILE_ITEM_REGISTRY))
		{
			this.items = ItemRegistry.loadRegistry(itemStream);
		}
		try (InputStream blockStream = _openModded(loader, mods, ModLayer.FILE_BLOCK_ASPECT);
			InputStream blockActiveStream = _openModded(loader, mods, ModLayer.FILE_BLOCK_ASPECT_ACTIVE)
		)
		{
			this.blocks = BlockAspect.loadRegistry(this.items
				, blockStream
				, blockActiveStream
			);
		}
		try (InputStream liquidStream = _openModded(loader, mods, ModLayer.FILE_LIQUID_REGISTRY))
		{
			this.liquids = LiquidRegistry.loadRegistry(this.items
				, this.blocks
				, liquidStream
			);
		}
		try (InputStream durabilityStream = _openModded(loader, mods, ModLayer.FILE_DURABILITY))
		{
			this.durability = DurabilityAspect.load(this.items
				, durabilityStream
			);
		}
		try (InputStream durabilityStream = _openModded(loader, mods, ModLayer.FILE_INVENTORY_ENCUMBRANCE))
		{
			this.encumbrance = InventoryEncumbrance.load(this.items
				, durabilityStream
			);
		}
		try (InputStream recipeStream = _openModded(loader, mods, ModLayer.FILE_CRAFTING_RECIPES))
		{
			this.crafting = CraftAspect.load(this.items
				, this.blocks
				, this.encumbrance
				, recipeStream
			);
		}
		try (InputStream toughnessStream = _openModded(loader, mods, ModLayer.FILE_TOUGHNESS))
		{
			this.damage = DamageAspect.load(this.items
				, this.blocks
				, toughnessStream
			);
		}
		try (InputStream fuelStream = _openModded(loader, mods, ModLayer.FILE_FUEL_MILLIS))
		{
			this.fuel = FuelAspect.load(this.items
				, fuelStream
			);
		}
		try (InputStream opacityStream = _openModded(loader, mods, ModLayer.FILE_LIGHT_OPACITY);
			InputStream sourceStream = _openModded(loader, mods, ModLayer.FILE_LIGHT_SOURCES);
			InputStream sourceActiveStream = _openModded(loader, mods, ModLayer.FILE_LIGHT_SOURCE_ACTIVE)
		)
		{
			this.lighting = LightAspect.load(this.items
				, this.blocks
				, opacityStream
				, sourceStream
				, sourceActiveStream
			);
		}
		try (InputStream plantStream = _openModded(loader, mods, ModLayer.FILE_PLANT_REGISTRY))
		{
			this.plants = PlantRegistry.load(this.items
				, this.blocks
				, plantStream
			);
		}
		try (InputStream foodStream = _openModded(loader, mods, ModLayer.FILE_FOODS))
		{
			this.foods = FoodRegistry.load(this.items
				, foodStream
			);
		}
		try (InputStream toolStream = _openModded(loader, mods, ModLayer.FILE_TOOL_REGISTRY))
		{
			this.tools = ToolRegistry.load(this.items
				, toolStream
			);
		}
		try (InputStream armourStream = _openModded(loader, mods, ModLayer.FILE_ARMOUR_REGISTRY))
		{
			this.armour = ArmourRegistry.load(this.items
				, armourStream
			);
		}
		try (InputStream stationStream = _openModded(loader, mods, ModLayer.FILE_STATION_REGISTRY))
		{
			this.stations = StationRegistry.load(this.items
				, this.blocks
				, this.crafting
				, stationStream
			);
		}
		try (InputStream logicStream = _openModded(loader, mods, ModLayer.FILE_LOGIC))
		{
			this.logic = LogicAspect.load(this.items
				, this.blocks
				, logicStream
			);
		}
		try (InputStream creatureStream = _openModded(loader, mods, ModLayer.FILE_CRETURE_REGISTRY))
		{
			this.creatures = CreatureRegistry.loadRegistry(this.items
				, creatureStream
			);
		}
		try (InputStream multiBlockStream = _openModded(loader, mods, ModLayer.FILE_MULTI_BLOCK_REGISTRY))
		{
			this.multiBlocks = MultiBlockRegistry.load(this.items
				, this.blocks
				, multiBlockStream
			);
		}
		try (InputStream compositeStream = _openModded(loader, mods, ModLayer.FILE_COMPOSITE_REGISTRY))
		{
			this.composites = CompositeRegistry.load(this.items
				, this.blocks
				, compositeStream
			);
		}
		try (InputStream groundCoverStream = _openModded(loader, mods, ModLayer.FILE_GROUND_COVER_REGISTRY))
		{
			this.groundCover = GroundCoverRegistry.load(this.items
				, this.blocks
				, groundCoverStream
			);
		}
		try (InputStream specialSlotStream = _openModded(loader, mods, ModLayer.FILE_SPECIAL_SLOT))
		{
			this.specialSlot = SpecialSlotAspect.load(this.items
				, this.blocks
				, specialSlotStream
			);
		}
		try (InputStream enchantingStream = _openModded(loader, mods, ModLayer.FILE_ENCHANTING);
			InputStream infusionsStream = _openModded(loader, mods, ModLayer.FILE_INFUSIONS);
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
		try (InputStream orientationStream = _openModded(loader, mods, ModLayer.FILE_ORIENTATION_ASPECT))
		{
			this.orientations = OrientationAspect.load(this.items
				, this.blocks
				, orientationStream
			);
		}
		try (InputStream specialConstantsStream = _openModded(loader, mods, ModLayer.FILE_SPECIAL_CONSTANTS))
		{
			this.special = SpecialConstants.load(this.items
				, this.blocks
				, specialConstantsStream
			);
		}
	}

	private static InputStream _openModded(ClassLoader loader, ModLayer[] mods, String fileName)
	{
		InputStream[] modStreams = Arrays.stream(mods)
			.map((ModLayer layer) -> {
				byte[] raw = layer.contents.get(fileName);
				InputStream open = null;
				if (null != raw)
				{
					open = new ByteArrayInputStream(raw);
				}
				return open;
			})
			.filter((InputStream possible) -> (null != possible))
			.toArray((int size) -> new InputStream[size])
		;
		InputStream baseline = ClassLoader.getSystemResourceAsStream(fileName);
		return new LayeredInputStream(baseline, modStreams);
	}
}
