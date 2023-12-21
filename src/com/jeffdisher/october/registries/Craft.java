package com.jeffdisher.october.registries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;


/**
 * A container of crafting activities.
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public enum Craft
{
	LOG_TO_PLANKS("Convert log to planks"
			, (Inventory inv) -> inv.items.containsKey(ItemRegistry.LOG)
			, (Inventory inv) -> {
				// In this case, we should leave encumbrance unchanged.
				Assert.assertTrue(ItemRegistry.LOG.encumbrance() == (ItemRegistry.PLANK.encumbrance() * 2));
				
				Items logs = inv.items.get(ItemRegistry.LOG);
				int newLogs = logs.count() - 1;
				Items planks = inv.items.get(ItemRegistry.PLANK);
				int newPlanks = (null != planks)
						? planks.count() + 2
						: 2
				;
				// Create the new map.
				Map<Item, Items> newMap = new HashMap<>(inv.items);
				if (newLogs > 0)
				{
					newMap.put(ItemRegistry.LOG, new Items(ItemRegistry.LOG, newLogs));
				}
				else
				{
					newMap.remove(ItemRegistry.LOG);
				}
				newMap.put(ItemRegistry.PLANK, new Items(ItemRegistry.PLANK, newPlanks));
				return new Inventory(inv.maxEncumbrance, newMap, inv.currentEncumbrance);
			}
			, 1000L),
	;

	public final String name;
	public final Function<Inventory, Boolean> checkValid;
	public final Function<Inventory, Inventory> applyCraft;
	public final long millisPerCraft;

	private Craft(String name
			, Function<Inventory, Boolean> checkValid
			, Function<Inventory, Inventory> applyCraft
			, long millisPerCraft
	)
	{
		this.name = name;
		this.checkValid = checkValid;
		this.applyCraft = applyCraft;
		this.millisPerCraft = millisPerCraft;
	}
}
