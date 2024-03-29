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

	private final BlockAspect _blocks;
	private final int[] _encumbranceByItemType;

	public InventoryAspect(ItemRegistry items, BlockAspect blocks)
	{
		_blocks = blocks;
		_encumbranceByItemType = new int[items.ITEMS_BY_TYPE.length];
		
		// We construct the encumbrance by looking at items.
		// This purely exists to demonstrate the shape of the Item data once it is fully external data:  All aspects are
		// out-of-line and their meaning for a specific Item type is answered through aspect-specific helpers, like this.
		// TODO:  Replace this with a data file later on.
		for (int i = 0; i < _encumbranceByItemType.length; ++i)
		{
			Item item = items.ITEMS_BY_TYPE[i];
			int encumbrance;
			if ((items.AIR == item)
					|| (items.WATER_SOURCE == item)
					|| (items.WATER_STRONG == item)
					|| (items.WATER_WEAK == item)
					|| (items.LEAF == item)
					|| (items.WHEAT_SEEDLING == item)
					|| (items.WHEAT_YOUNG == item)
					|| (items.WHEAT_MATURE == item)
			)
			{
				encumbrance = ENCUMBRANCE_NON_INVENTORY;
			}
			else if ((items.PLANK == item)
					|| (items.CHARCOAL == item)
					|| (items.DIRT == item)
					|| (items.LANTERN == item)
					|| (items.SAPLING == item)
					|| (items.WHEAT_SEED == item)
					|| (items.WHEAT_ITEM == item)
			)
			{
				encumbrance = 1;
			}
			else if ((items.STONE == item)
					|| (items.STONE_BRICK == item)
					|| (items.LOG == item)
					|| (items.CRAFTING_TABLE == item)
					|| (items.COAL_ORE == item)
					|| (items.LANTERN == item)
					|| (items.IRON_INGOT == item)
			)
			{
				encumbrance = 2;
			}
			else if ((items.FURNACE == item)
					|| (items.IRON_ORE == item)
			)
			{
				encumbrance = 4;
			}
			else
			{
				// We need to add this entry.
				encumbrance = ENCUMBRANCE_UNKNOWN;
			}
			_encumbranceByItemType[i] = encumbrance;
		}
	}

	public int getInventoryCapacity(Block block)
	{
		// Here, we will opt-in to specific item types, only returning 0 if the block type has no inventory.
		int size;
		// We will treat any block where the entity can walk as an "air inventory".
		if (_blocks.permitsEntityMovement(block))
		{
			size = CAPACITY_AIR;
		}
		else if ((_blocks.CRAFTING_TABLE == block)
				|| (_blocks.FURNACE == block)
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

	public int getEncumbrance(Item item)
	{
		return _encumbranceByItemType[item.number()];
	}
}
