package com.jeffdisher.october.registries;

import java.util.List;
import java.util.Set;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.utils.Assert;


/**
 * A container of crafting activities.
 * A crafting activity has inputs, outputs, a time cost, and a classification (used for filtering).
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public enum Craft
{
	LOG_TO_PLANKS("Convert log to planks"
			, Classification.TRIVIAL
			, new Items(ItemRegistry.LOG, 1)
			, new Items(ItemRegistry.PLANK, 2)
			, 1000L
	),
	STONE_TO_STONE_BRICK("Convert stone to stone brick"
			, Classification.TRIVIAL
			, new Items(ItemRegistry.STONE, 1)
			, new Items(ItemRegistry.STONE_BRICK, 1)
			, 2000L
	),
	PLANKS_TO_CRAFTING_TABLE("Convert wood planks into a crafting table"
			, Classification.TRIVIAL
			, new Items(ItemRegistry.PLANK, 4)
			, new Items(ItemRegistry.CRAFTING_TABLE, 1)
			, 4000L
	),
	STONE_BRICKS_TO_FURNACE("Convert stone bricks into a furnace"
			, Classification.COMMON
			, new Items(ItemRegistry.STONE_BRICK, 4)
			, new Items(ItemRegistry.FURNACE, 1)
			, 8000L
	),
	// For short-term testing, we define a furnace recipe for creating logs from bricks.
	TEMP_FURNACE_BRICKS_TO_LOGS("Converts bricks to logs in a furnace"
			, Classification.SPECIAL_FURNACE
			, new Items(ItemRegistry.STONE_BRICK, 1)
			, new Items(ItemRegistry.LOG, 1)
			, 1000L
	),
	;

	/**
	 * Used to categorize crafting recipes since they may only be applicable in certain environments.
	 */
	public static enum Classification
	{
		/**
		 * Cannot be used.
		 */
		ERROR,
		/**
		 * Crafting operations so trivial that they can be done directly in-inventory.
		 */
		TRIVIAL,
		/**
		 * Crafting operations which require a crafting table.
		 */
		COMMON,
		/**
		 * A special crafting operation which happens automatically within a furnace.
		 */
		SPECIAL_FURNACE,
	}

	/**
	 * Returns the list of crafting operations which are included in the given set of classifications.
	 * 
	 * @param classifications The set of classifications to check.
	 * @return The list of crafting operations in the union of these classifications.
	 */
	public static List<Craft> craftsForClassifications(Set<Classification> classifications)
	{
		return List.of(Craft.values()).stream().filter((Craft craft) -> classifications.contains(craft.classification)).toList();
	}


	public final String name;
	public final Classification classification;
	public final Items input;
	public final Items output;
	public final long millisPerCraft;

	private Craft(String name
			, Classification classification
			, Items input
			, Items output
			, long millisPerCraft
	)
	{
		Assert.assertTrue(Classification.ERROR != classification);
		// We never want to allow encumbrance to increase through crafting.
		int inputEncumbrance = input.type().encumbrance() * input.count();
		int outputEncumbrance = output.type().encumbrance() * output.count();
		Assert.assertTrue(inputEncumbrance >= outputEncumbrance);
		
		this.name = name;
		this.classification = classification;
		this.input = input;
		this.output = output;
		this.millisPerCraft = millisPerCraft;
	}

	public boolean canApply(Inventory inv)
	{
		return inv.getCount(this.input.type()) >= this.input.count();
	}

	public boolean craft(MutableInventory inv)
	{
		boolean didCraft = false;
		// Verify that they have the input.
		if (inv.getCount(this.input.type()) >= this.input.count())
		{
			// Now, perform the craft against this inventory.
			inv.removeItems(this.input.type(), this.input.count());
			boolean didAdd = inv.addAllItems(this.output.type(), this.output.count());
			// We can't fail to add here.
			Assert.assertTrue(didAdd);
			didCraft = true;
		}
		return didCraft;
	}
}
