package com.jeffdisher.october.types;

import com.jeffdisher.october.aspects.InventoryAspect;
import com.jeffdisher.october.utils.Assert;


/**
 * Used to describe a specific crafting operation, including how it is classified, inputs, output, and time cost.
 */
public class Craft
{
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


	public final short number;
	public final String name;
	public final Classification classification;
	public final Items[] input;
	public final Items output;
	public final long millisPerCraft;

	public Craft(short number
			, String name
			, Classification classification
			, Items[] input
			, Items output
			, long millisPerCraft
	)
	{
		Assert.assertTrue(number >= 0);
		Assert.assertTrue(Classification.ERROR != classification);
		// We never want to allow encumbrance to increase through crafting.
		int inputEncumbrance = 0;
		for (Items oneInput : input)
		{
			inputEncumbrance += InventoryAspect.getEncumbrance(oneInput.type()) * oneInput.count();
		}
		int outputEncumbrance = InventoryAspect.getEncumbrance(output.type()) * output.count();
		Assert.assertTrue(inputEncumbrance >= outputEncumbrance);
		
		this.number = number;
		this.name = name;
		this.classification = classification;
		this.input = input;
		this.output = output;
		this.millisPerCraft = millisPerCraft;
	}
}
