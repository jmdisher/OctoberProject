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
	public final InventoryAspect inventory;
	public final CraftAspect crafting;
	public final DamageAspect damage;
	public final FuelAspect fuel;
	public final LightAspect lighting;
	public final PlantRegistry plants;

	private Environment() throws IOException, TabListException
	{
		// Local instantiation only.
		ClassLoader loader = getClass().getClassLoader();
		this.items = ItemRegistry.loadRegistry(loader.getResourceAsStream("item_registry.tablist"));
		this.blocks = new BlockAspect(this.items);
		this.inventory = new InventoryAspect(this.items, this.blocks);
		this.crafting = new CraftAspect(this.items, this.inventory);
		this.damage = new DamageAspect(this.blocks);
		this.fuel = new FuelAspect(this.items, this.blocks);
		this.lighting = new LightAspect(this.blocks);
		this.plants = new PlantRegistry(this.blocks);
	}
}
