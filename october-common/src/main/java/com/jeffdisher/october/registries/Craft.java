package com.jeffdisher.october.registries;

import java.util.function.Function;

import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.utils.Assert;


/**
 * A container of crafting activities.
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public enum Craft
{
	LOG_TO_PLANKS("Convert log to planks"
			, (MutableInventory inv) -> {
				// In this case, we should leave encumbrance unchanged.
				Assert.assertTrue(ItemRegistry.LOG.encumbrance() == (ItemRegistry.PLANK.encumbrance() * 2));
				
				boolean didApply = false;
				if (inv.getCount(ItemRegistry.LOG) > 0)
				{
					inv.removeItems(ItemRegistry.LOG, 1);
					inv.addItems(ItemRegistry.PLANK, 2);
					didApply = true;
				}
				return didApply;
			}
			, 1000L),
	;

	public final String name;
	public final Function<MutableInventory, Boolean> craft;
	public final long millisPerCraft;

	private Craft(String name
			, Function<MutableInventory, Boolean> craft
			, long millisPerCraft
	)
	{
		this.name = name;
		this.craft = craft;
		this.millisPerCraft = millisPerCraft;
	}
}
