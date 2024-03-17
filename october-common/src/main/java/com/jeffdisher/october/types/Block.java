package com.jeffdisher.october.types;

import com.jeffdisher.october.registries.ItemRegistry;


/**
 * Represents the subset of Item objects which can be placed in the world.  This type is just to provide a high-level
 * distinction between Item and Block so they just wrap the number.
 */
public record Block(short number)
{
	/**
	 * @return The item representation of this block.
	 */
	public Item asItem()
	{
		return _asItem();
	}

	@Override
	public String toString()
	{
		return "Block(" + _asItem().name() +")";
	}

	/**
	 * Used to determine if this block is something like air/water/etc.
	 * Note that those which cannot be replaced are potentially breakable (although could be indestructible).
	 * 
	 * @return True if this block can be directly overwritten by another.
	 */
	public boolean canBeReplaced()
	{
		Item item = _asItem();
		return (ItemRegistry.AIR == item)
				|| (ItemRegistry.WATER_SOURCE == item)
				|| (ItemRegistry.WATER_STRONG == item)
				|| (ItemRegistry.WATER_WEAK == item)
		;
	}


	private Item _asItem()
	{
		return ItemRegistry.ITEMS_BY_TYPE[number];
	}
}
