package com.jeffdisher.october.registries;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.utils.Assert;


/**
 * A container of crafting activities.
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public enum Craft
{
	LOG_TO_PLANKS("Convert log to planks"
			, new Items(ItemRegistry.LOG, 1)
			, new Items(ItemRegistry.PLANK, 2)
			, 1000L
	),
	STONE_TO_STONE_BRICK("Convert stone to stone brick"
			, new Items(ItemRegistry.STONE, 1)
			, new Items(ItemRegistry.STONE_BRICK, 1)
			, 2000L
	),
	;

	public final String name;
	public final Items input;
	public final Items output;
	public final long millisPerCraft;

	private Craft(String name
			, Items input
			, Items output
			, long millisPerCraft
	)
	{
		// We never want to allow encumbrance to increase through crafting.
		int inputEncumbrance = input.type().encumbrance() * input.count();
		int outputEncumbrance = output.type().encumbrance() * output.count();
		Assert.assertTrue(inputEncumbrance >= outputEncumbrance);
		
		this.name = name;
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
