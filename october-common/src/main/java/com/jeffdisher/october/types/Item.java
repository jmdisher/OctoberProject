package com.jeffdisher.october.types;


/**
 * Represents an item type which can be in an inventory.  There is one instance for each type in the system.
 * Note that positive numbers are also blocks which can be placed in the world, while negative numbers are
 * inventory-only (and 0 is air, of course).
 * We don't have a concept analogous to Minecraft's "item stack", instead using "encumbrance" to limit how many items
 * can be stored in a given inventory, more like how Project Zomboid does.
 */
public record Item(String name
		, short number
		, int encumbrance
		, short toughness
) {
}
