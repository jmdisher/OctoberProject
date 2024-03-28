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


	private Item _asItem()
	{
		return ItemRegistry.ITEMS_BY_TYPE[number];
	}
}
