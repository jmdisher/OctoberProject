package com.jeffdisher.october.aspects;

import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


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

	public static int getSizeForType(Item item)
	{
		// Here, we will opt-in to specific item types, only returning 0 if the block type has no inventory.
		int size;
		switch (item.number())
		{
			case BlockAspect.AIR:
				size = CAPACITY_AIR;
				break;
			case BlockAspect.CRAFTING_TABLE:
				size = CAPACITY_CRAFTING_TABLE;
				break;
			case BlockAspect.STONE:
			case BlockAspect.STONE_BRICK:
			case BlockAspect.LOG:
			case BlockAspect.PLANK:
				size = 0;
				break;
			default:
				// We need to add this entry.
				throw Assert.unreachable();
		}
		return size;
	}
}
