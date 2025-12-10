package com.jeffdisher.october.types;

import java.util.Map;

import com.jeffdisher.october.properties.PropertyType;


/**
 * Note that the "number" must be a positive integer as "0" reserved in serialization as "null".
 */
public record Enchantment(int number
	, Block table
	, long millisToApply
	, Item targetItem
	, Map<Item, Integer> consumedItems
	, PropertyType<Byte> enchantmentToApply
)
{
}
