package com.jeffdisher.october.aspects;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.Craft.Classification;
import com.jeffdisher.october.utils.Assert;


/**
 * Describes the crafting aspect and how Craft operations are looked up and applied.
 * This will likely be changed into data once all the core crafting mechanics have been fleshed out.
 */
public class CraftAspect
{
	// Variables used when initially populating the table.
	private List<Craft> _crafts = new ArrayList<>();

	// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
	// negative number are for items which cannot be placed.
	public final Craft LOG_TO_PLANKS;
	public final Craft STONE_TO_STONE_BRICK;
	public final Craft PLANKS_TO_CRAFTING_TABLE;
	public final Craft STONE_BRICKS_TO_FURNACE;
	public final Craft CRAFT_LANTERN;
	public final Craft FURNACE_LOGS_TO_CHARCOAL;
	public final Craft FURNACE_SMELT_IRON;

	private Craft _register(String name
			, Classification classification
			, Items[] input
			, Items output
			, long millisPerCraft
	)
	{
		int number = _crafts.size();
		Assert.assertTrue(number <= Short.MAX_VALUE);
		Craft craft = new Craft((short)number
				, name
				, classification
				, input
				, output
				, millisPerCraft
		);
		_crafts.add(craft);
		return craft;
	}

	/**
	 * Since blocks are the non-negative item types, this helper exists to look them up by block type.
	 */
	public final Craft[] CRAFTING_OPERATIONS;

	public CraftAspect(ItemRegistry items, InventoryAspect inventory)
	{
		// The rules governing items is that non-negative short values are reserved for items which are also blocks, while
		// negative number are for items which cannot be placed.
		this.LOG_TO_PLANKS = _register("Convert log to planks"
				, Craft.Classification.TRIVIAL
				, new Items[] {
					new Items(items.LOG, 1),
				}
				, new Items(items.PLANK, 2)
				, 1000L
		);
		this.STONE_TO_STONE_BRICK = _register("Convert stone to stone brick"
				, Craft.Classification.TRIVIAL
				, new Items[] {
					new Items(items.STONE, 1),
				}
				, new Items(items.STONE_BRICK, 1)
				, 2000L
		);
		this.PLANKS_TO_CRAFTING_TABLE = _register("Convert wood planks into a crafting table"
				, Craft.Classification.TRIVIAL
				, new Items[] {
					new Items(items.PLANK, 4),
				}
				, new Items(items.CRAFTING_TABLE, 1)
				, 4000L
		);
		this.STONE_BRICKS_TO_FURNACE = _register("Convert stone bricks into a furnace"
				, Craft.Classification.COMMON
				, new Items[] {
					new Items(items.STONE_BRICK, 4),
				}
				, new Items(items.FURNACE, 1)
				, 8000L
		);
		this.CRAFT_LANTERN = _register("Craft a lantern"
				, Craft.Classification.COMMON
				, new Items[] {
					new Items(items.IRON_INGOT, 2),
					new Items(items.CHARCOAL, 1),
				}
				, new Items(items.LANTERN, 1)
				, 4000L
		);
		this.FURNACE_LOGS_TO_CHARCOAL = _register("Converts logs to charcoal in a furnace"
				, Craft.Classification.SPECIAL_FURNACE
				, new Items[] {
					new Items(items.LOG, 1),
				}
				, new Items(items.CHARCOAL, 1)
				, 1000L
		);
		this.FURNACE_SMELT_IRON = _register("Converts iron ore to ingot in a furnace"
				, Craft.Classification.SPECIAL_FURNACE
				, new Items[] {
					new Items(items.IRON_ORE, 1),
				}
				, new Items(items.IRON_INGOT, 1)
				, 2000L
		);
		
		// We never want to allow encumbrance to increase through crafting.
		for (Craft craft : _crafts)
		{
			int inputEncumbrance = 0;
			for (Items oneInput : craft.input)
			{
				inputEncumbrance += inventory.getEncumbrance(oneInput.type()) * oneInput.count();
			}
			int outputEncumbrance = inventory.getEncumbrance(craft.output.type()) * craft.output.count();
			Assert.assertTrue(inputEncumbrance >= outputEncumbrance);
		}
		
		// Convert the items into the array as it is now fixed (since the static fields are initialized before this is called).
		CRAFTING_OPERATIONS = _crafts.toArray((int size) -> new Craft[size]);
		_crafts = null;
	}


	/**
	 * Returns the list of crafting operations which are included in the given set of classifications.
	 * 
	 * @param classifications The set of classifications to check.
	 * @return The list of crafting operations in the union of these classifications.
	 */
	public List<Craft> craftsForClassifications(Set<Craft.Classification> classifications)
	{
		return List.of(CRAFTING_OPERATIONS).stream().filter((Craft craft) -> classifications.contains(craft.classification)).toList();
	}

	public static boolean canApply(Craft craft, Inventory inv)
	{
		return _canApply(craft, inv);
	}

	public static boolean craft(Craft craft, MutableInventory inv)
	{
		boolean didCraft = false;
		// Verify that they have the input.
		if (_canApply(craft, inv.freeze()))
		{
			// Now, perform the craft against this inventory.
			for (Items items : craft.input)
			{
				inv.removeItems(items.type(), items.count());
			}
			boolean didAdd = inv.addAllItems(craft.output.type(), craft.output.count());
			// We can't fail to add here.
			Assert.assertTrue(didAdd);
			didCraft = true;
		}
		return didCraft;
	}


	private static boolean _canApply(Craft craft, Inventory inv)
	{
		boolean canApply = true;
		for (Items items : craft.input)
		{
			canApply = inv.getCount(items.type()) >= items.count();
			if (!canApply)
			{
				break;
			}
		}
		return canApply;
	}
}
