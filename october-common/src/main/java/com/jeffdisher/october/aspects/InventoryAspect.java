package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


/**
 * Contains constants and helpers associated with the inventory aspect.
 */
public class InventoryAspect
{
	/**
	 * The capacity of an air block is small since it is just "on the ground" and we want containers to be used.
	 */
	public static final int CAPACITY_AIR = 10;
	public static final int CAPACITY_PLAYER = 20;
	public static final int CAPACITY_CRAFTING_TABLE = CAPACITY_AIR;

	// We will default to an encumbrance of "4" for undefined types (since it is big but not overwhelming.
	public static final int ENCUMBRANCE_UNKNOWN = 4;
	// Note that every item has a number and encumbrance, 0 is used for any item which cannot exist in an inventory.
	public static final int ENCUMBRANCE_NON_INVENTORY = 0;

	public static final int[] ENCUMBRANCE_BY_TYPE = new int[ItemRegistry.ITEMS_BY_TYPE.length];

	static {
		// We construct the encumbrance by looking at items.
		// This purely exists to demonstrate the shape of the Item data once it is fully external data:  All aspects are
		// out-of-line and their meaning for a specific Item type is answered through aspect-specific helpers, like this.
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < ENCUMBRANCE_BY_TYPE.length; ++i)
		{
			Item item = ItemRegistry.ITEMS_BY_TYPE[i];
			int encumbrance;
			if ((ItemRegistry.AIR == item)
					|| (ItemRegistry.WATER_SOURCE == item)
					|| (ItemRegistry.WATER_STRONG == item)
					|| (ItemRegistry.WATER_WEAK == item)
					|| (ItemRegistry.LEAF == item)
					|| (ItemRegistry.WHEAT_SEEDLING == item)
					|| (ItemRegistry.WHEAT_YOUNG == item)
					|| (ItemRegistry.WHEAT_MATURE == item)
			)
			{
				encumbrance = ENCUMBRANCE_NON_INVENTORY;
			}
			else if ((ItemRegistry.PLANK == item)
					|| (ItemRegistry.CHARCOAL == item)
					|| (ItemRegistry.DIRT == item)
					|| (ItemRegistry.LANTERN == item)
					|| (ItemRegistry.SAPLING == item)
					|| (ItemRegistry.WHEAT_SEED == item)
					|| (ItemRegistry.WHEAT_ITEM == item)
			)
			{
				encumbrance = 1;
			}
			else if ((ItemRegistry.STONE == item)
					|| (ItemRegistry.STONE_BRICK == item)
					|| (ItemRegistry.LOG == item)
					|| (ItemRegistry.CRAFTING_TABLE == item)
					|| (ItemRegistry.COAL_ORE == item)
					|| (ItemRegistry.LANTERN == item)
					|| (ItemRegistry.IRON_INGOT == item)
			)
			{
				encumbrance = 2;
			}
			else if ((ItemRegistry.FURNACE == item)
					|| (ItemRegistry.IRON_ORE == item)
			)
			{
				encumbrance = 4;
			}
			else
			{
				// We need to add this entry.
				encumbrance = ENCUMBRANCE_UNKNOWN;
			}
			ENCUMBRANCE_BY_TYPE[i] = encumbrance;
		}
	}

	public static int getInventoryCapacity(Block block)
	{
		// Here, we will opt-in to specific item types, only returning 0 if the block type has no inventory.
		int size;
		// We will treat any block where the entity can walk as an "air inventory".
		if (BlockAspect.permitsEntityMovement(block))
		{
			size = CAPACITY_AIR;
		}
		else if ((BlockAspect.CRAFTING_TABLE == block)
				|| (BlockAspect.FURNACE == block)
		)
		{
			size = CAPACITY_CRAFTING_TABLE;
		}
		else
		{
			// We default to 0.
			size = 0;
		}
		return size;
	}

	public static int getEncumbrance(Item item)
	{
		return ENCUMBRANCE_BY_TYPE[item.number()];
	}
}
