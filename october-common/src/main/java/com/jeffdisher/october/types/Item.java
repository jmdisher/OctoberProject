package com.jeffdisher.october.types;


/**
 * Represents an item type which can be in an inventory.  There is one instance for each type in the system.
 * All instances are expected to have a non-negative "number".
 * Note that the current design approach is to make the Item into the lowest-level logical primitive of the world.  That
 * means that it only has a name and assigned item number but incorporates no other information related to higher-level
 * concepts build on top of it.
 * Other aspects which need to attribute further meaning to an item are expected to use the number to look up that
 * information in some out-of-line storage or algorithm.
 */
public record Item(String name
		, short number
) {
}
