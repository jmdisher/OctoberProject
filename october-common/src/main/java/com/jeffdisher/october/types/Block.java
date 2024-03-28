package com.jeffdisher.october.types;


/**
 * Represents the subset of Item objects which can be placed in the world.  This type is just to provide a high-level
 * distinction between Item and Block since they do have different implications.
 */
public record Block(Item item)
{
	@Override
	public String toString()
	{
		return "Block(" + item.name() +")";
	}
}
