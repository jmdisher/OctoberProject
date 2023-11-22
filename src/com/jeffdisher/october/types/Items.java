package com.jeffdisher.october.types;


/**
 * An instance of a group of items in a single inventory.  The "Item" elements are all singletons so this just describes
 * how many of a given type are in an inventory.
 */
public record Items (Item type
		, int count
) {}
